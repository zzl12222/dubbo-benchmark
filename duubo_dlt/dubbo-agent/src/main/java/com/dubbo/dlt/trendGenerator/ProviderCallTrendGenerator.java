package com.dubbo.dlt.trendGenerator;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dubbo.common.entry.CpuMemTrendData;
import com.dubbo.dlt.trendGenerator.entry.CallRecord;
import com.dubbo.dlt.trendGenerator.entry.TrendData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ProviderCallTrendGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ProviderCallTrendGenerator.class);

    private static final DateTimeFormatter SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 核心入口方法 - 最终修正版：严格过滤非选中方法折线
     */
    public static void generateCallTrendHtml(String rawEscapeJson, String htmlGeneratePath) throws IOException {
        String cleanJson = cleanEscapeJson(rawEscapeJson);
        logger.info("JSON转义清理完成，开始解析数据");

        JSONObject rootJson = JSON.parseObject(cleanJson);
        List<CallRecord> callRecords = parseCallRecords(rootJson);
        List<CpuMemTrendData> cpuMemTrendDataList = parseCpuMemData(rootJson);

        // 接口调用趋势数据（秒级+分钟级）
        List<TrendData> secondTrendData = aggregateDynamicData(callRecords, "second");
        String secondDataJson = JSON.toJSONString(secondTrendData);
        List<TrendData> minuteTrendData = aggregateDynamicData(callRecords, "minute");
        String minuteDataJson = JSON.toJSONString(minuteTrendData);

        // CPU内存趋势数据（秒级原始+分钟级聚合）
        String cpuMemSecondDataJson = JSON.toJSONString(cpuMemTrendDataList);
        List<CpuMemTrendData> cpuMemMinuteTrendDataList = aggregateCpuMemData(cpuMemTrendDataList, "minute");
        String cpuMemMinuteDataJson = JSON.toJSONString(cpuMemMinuteTrendDataList);

        // 提取所有唯一方法名 传给前端做下拉选择
        Set<String> uniqueMethods = getUniqueMethodNames(callRecords);
        String methodNamesJson = JSON.toJSONString(uniqueMethods);

        String htmlTemplate = getDynamicEchartsHtmlTemplate();
        String finalHtml = htmlTemplate
                .replace("{{SECOND_DATA}}", secondDataJson)
                .replace("{{MINUTE_DATA}}", minuteDataJson)
                .replace("{{CPU_MEM_SECOND_DATA}}", cpuMemSecondDataJson)
                .replace("{{CPU_MEM_MINUTE_DATA}}", cpuMemMinuteDataJson)
                .replace("{{METHOD_NAMES}}", methodNamesJson);

        writeHtmlToFile(finalHtml, htmlGeneratePath);
        logger.info("✅ 动态趋势图生成完成！路径：{}，共解析到【{}】个不同的接口方法", htmlGeneratePath, uniqueMethods.size());
    }

    /**
     * 原有方法：聚合接口调用的趋势数据（秒/分钟维度）
     */
    private static List<TrendData> aggregateDynamicData(List<CallRecord> callRecords, String timeUnit) {
        Map<String, List<CallRecord>> timeGroupMap;
        if ("second".equalsIgnoreCase(timeUnit)) {
            timeGroupMap = callRecords.stream().collect(Collectors.groupingBy(r -> r.getStartTimeDt().format(SECOND_FORMAT)));
        } else {
            timeGroupMap = callRecords.stream().collect(Collectors.groupingBy(r -> r.getStartTimeDt().format(MINUTE_FORMAT)));
        }
        List<TrendData> trendDataList = new ArrayList<>();
        for (Map.Entry<String, List<CallRecord>> entry : timeGroupMap.entrySet()) {
            String time = entry.getKey();
            List<CallRecord> records = entry.getValue();

            TrendData trendData = new TrendData();
            trendData.setTime(time);
            trendData.setTotalCount(records.size());

            long successNum = records.stream().filter(CallRecord::isSuccess).count();
            double successRate = records.size() == 0 ? 0 : (successNum * 100.0) / records.size();
            trendData.setSuccessRate(successRate);

            Map<String, Integer> methodCountMap = records.stream()
                    .collect(Collectors.groupingBy(CallRecord::getMethodName, Collectors.summingInt(e -> 1)));
            trendData.setMethodCountMap(methodCountMap);

            trendDataList.add(trendData);
        }
        trendDataList.sort(Comparator.comparing(TrendData::getTime));
        return trendDataList;
    }

    /**
     * CPU/JVM内存数据按【分钟】聚合，取平均值
     */
    private static List<CpuMemTrendData> aggregateCpuMemData(List<CpuMemTrendData> cpuMemList, String timeUnit) {
        if (!"minute".equalsIgnoreCase(timeUnit) || cpuMemList.isEmpty()) {
            return cpuMemList;
        }
        Map<String, List<CpuMemTrendData>> minuteGroupMap = cpuMemList.stream()
                .collect(Collectors.groupingBy(data -> LocalDateTime.parse(data.getTime(), SECOND_FORMAT).format(MINUTE_FORMAT)));

        List<CpuMemTrendData> minuteDataList = new ArrayList<>();
        for (Map.Entry<String, List<CpuMemTrendData>> entry : minuteGroupMap.entrySet()) {
            String minuteTime = entry.getKey();
            List<CpuMemTrendData> secDataList = entry.getValue();

            CpuMemTrendData minuteData = new CpuMemTrendData();
            minuteData.setTime(minuteTime);
            int avgCpu = secDataList.stream().mapToInt(CpuMemTrendData::getCpuUsage).sum() / secDataList.size();
            int avgMem = secDataList.stream().mapToInt(CpuMemTrendData::getMemoryUsage).sum() / secDataList.size();
            minuteData.setCpuUsage(avgCpu);
            minuteData.setMemoryUsage(avgMem);
            minuteDataList.add(minuteData);
        }
        minuteDataList.sort(Comparator.comparing(CpuMemTrendData::getTime));
        return minuteDataList;
    }

    /**
     * 获取所有唯一的接口方法名
     */
    private static Set<String> getUniqueMethodNames(List<CallRecord> callRecords) {
        return callRecords.stream().map(CallRecord::getMethodName).collect(Collectors.toSet());
    }

    /**
     * 清理JSON转义字符
     */
    public static String cleanEscapeJson(String escapeJson) {
        if (escapeJson == null || escapeJson.isEmpty()) {
            return "";
        }
        String cleanJson = escapeJson.replaceAll("\\\\", "").replaceAll("^\"|\"$", "");
        Object jsonObj = JSON.parse(cleanJson);
        return JSONObject.toJSONString(jsonObj);
    }

    /**
     * 解析接口调用数据
     */
    public static List<CallRecord> parseCallRecords(JSONObject root) {
        List<CallRecord> records = new ArrayList<>();
        JSONObject allResults = root.getJSONObject("allResults");
        JSONObject providerObj = allResults.getJSONObject("TestService-provider");
        List<JSONObject> list = providerObj.getJSONArray("provideResultList").toJavaList(JSONObject.class);

        for (JSONObject item : list) {
            String methodName = item.getString("methodName");
            String startTime = item.getString("startTime");
            boolean success = item.getBooleanValue("success");
            records.add(new CallRecord(methodName, startTime, success));
        }
        return records;
    }

    /**
     * 解析CPU使用率+JVM内存使用率，生成每秒趋势数据
     */
    public static List<CpuMemTrendData> parseCpuMemData(JSONObject root) {
        List<CpuMemTrendData> cpuMemList = new ArrayList<>();
        String cpuStartTime = root.getString("cpuStartTime");
        String cpuEndTime = root.getString("cpuEndTime");
        JSONObject cpuUsageJson = root.getJSONObject("cpuUsage");
        JSONObject memoryUsageJson = root.getJSONObject("memoryUsage");

        if (cpuUsageJson == null || memoryUsageJson == null || cpuStartTime == null) {
            return cpuMemList;
        }
        LocalDateTime startDt = LocalDateTime.parse(cpuStartTime.substring(0, 19), SECOND_FORMAT);
        Set<String> cpuKeySet = cpuUsageJson.keySet();

        for (String secondKey : cpuKeySet) {
            int secondStep = Integer.parseInt(secondKey);
            LocalDateTime currDt = startDt.plusSeconds(secondStep - 1);
            String currTime = currDt.format(SECOND_FORMAT);

            CpuMemTrendData data = new CpuMemTrendData();
            data.setTime(currTime);
            data.setCpuUsage(cpuUsageJson.getInteger(secondKey));
            data.setMemoryUsage(memoryUsageJson.getInteger(secondKey));
            cpuMemList.add(data);
        }
        cpuMemList.sort(Comparator.comparing(CpuMemTrendData::getTime));
        return cpuMemList;
    }

    /**
     * 写入HTML文件到指定路径
     */
    public static void writeHtmlToFile(String htmlContent, String filePath) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        Files.write(file.toPath(), htmlContent.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * ✅ 核心修正：HTML模板中renderCallChart方法，严格过滤非选中方法
     */
    private static String getDynamicEchartsHtmlTemplate() {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n"
                + "<head>\n"
                + "    <meta charset=\"UTF-8\">\n"
                + "    <title>接口调用趋势+CPU&JVM内存使用率趋势图（独立方法+联动切换）</title>\n"
                + "    <script src=\"https://cdn.bootcdn.net/ajax/libs/echarts/5.4.3/echarts.min.js\"></script>\n"
                + "    <style>\n"
                + "        body { padding: 20px; }\n"
                + "        .chart-container { width: 100%; height: 550px; margin-top: 20px; border: 1px solid #f0f0f0; border-radius: 8px; padding: 10px; box-sizing: border-box;}\n"
                + "        .btn-group { margin-bottom: 15px; display: flex; align-items: center; gap: 20px; flex-wrap: wrap;}\n"
                + "        button { \n"
                + "            padding: 8px 20px; margin-right: 10px; \n"
                + "            border: none; background: #1677ff; color: #fff; border-radius: 4px; cursor: pointer;\n"
                + "        }\n"
                + "        button:hover { background: #0d5bbd; }\n"
                + "        button.active { background: #0d5bbd; border: 1px solid #004094; }\n"
                + "        select {\n"
                + "            padding: 8px 15px; border: 1px solid #1677ff; border-radius:4px; min-width:200px;\n"
                + "            color:#1677ff; font-size:14px;\n"
                + "        }\n"
                + "        h3 { color: #1677ff; margin-bottom: 10px; margin-top: 30px; }\n"
                + "        .select-label{color:#666;font-size:14px;}\n"
                + "    </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "    <div class=\"btn-group\">\n"
                + "        <div>\n"
                + "            <button id=\"secondBtn\" class=\"active\" onclick=\"changeTimeUnit('second')\">按【秒】展示</button>\n"
                + "            <button id=\"minuteBtn\" onclick=\"changeTimeUnit('minute')\">按【分钟】展示</button>\n"
                + "        </div>\n"
                + "        <div style='display:flex;align-items:center;'>\n"
                + "            <span class='select-label'>选择接口方法：</span>\n"
                + "            <select id=\"methodSelect\" onchange=\"changeSelectedMethod(this.value)\">\n"
                + "                <option value=\"all\">展示全部方法</option>\n"
                + "            </select>\n"
                + "        </div>\n"
                + "    </div>\n"
                + "    <h3>一、接口调用趋势统计</h3>\n"
                + "    <div class=\"chart-container\" id=\"callTrendChart\"></div>\n"
                + "    <h3>二、CPU使用率 & JVM内存使用率 趋势统计</h3>\n"
                + "    <div class=\"chart-container\" id=\"cpuMemTrendChart\"></div>\n"
                + "\n"
                + "    <script>\n"
                + "        // 后端传递的所有动态数据\n"
                + "        const secondData = {{SECOND_DATA}};\n"
                + "        const minuteData = {{MINUTE_DATA}};\n"
                + "        const cpuMemSecondData = {{CPU_MEM_SECOND_DATA}};\n"
                + "        const cpuMemMinuteData = {{CPU_MEM_MINUTE_DATA}};\n"
                + "        const methodNames = {{METHOD_NAMES}};\n"
                + "        \n"
                + "        // 全局状态变量\n"
                + "        let currentUnit = 'second';\n"
                + "        let currentData = secondData;\n"
                + "        let currentCpuMemData = cpuMemSecondData;\n"
                + "        let selectedMethod = 'all';\n"
                + "        \n"
                + "        // 初始化图表实例\n"
                + "        const callChart = echarts.init(document.getElementById('callTrendChart'));\n"
                + "        const cpuMemChart = echarts.init(document.getElementById('cpuMemTrendChart'));\n"
                + "        \n"
                + "        // 初始化方法下拉选择框\n"
                + "        initMethodSelect();\n"
                + "        // 初始化渲染图表\n"
                + "        renderCallChart();\n"
                + "        renderCpuMemChart();\n"
                + "        \n"
                + "        // 初始化下拉框选项\n"
                + "        function initMethodSelect() {\n"
                + "            const select = document.getElementById('methodSelect');\n"
                + "            methodNames.forEach(method => {\n"
                + "                const option = document.createElement('option');\n"
                + "                option.value = method;\n"
                + "                option.textContent = method;\n"
                + "                select.appendChild(option);\n"
                + "            });\n"
                + "        }\n"
                + "        \n"
                + "        // 切换秒/分钟维度 - 联动所有图表\n"
                + "        function changeTimeUnit(unit) {\n"
                + "            currentUnit = unit;\n"
                + "            currentData = unit === 'second' ? secondData : minuteData;\n"
                + "            currentCpuMemData = unit === 'second' ? cpuMemSecondData : cpuMemMinuteData;\n"
                + "            document.getElementById('secondBtn').className = unit === 'second' ? 'active' : '';\n"
                + "            document.getElementById('minuteBtn').className = unit === 'minute' ? 'active' : '';\n"
                + "            renderCallChart();\n"
                + "            renderCpuMemChart();\n"
                + "        }\n"
                + "        \n"
                + "        // 切换选中的接口方法\n"
                + "        function changeSelectedMethod(method) {\n"
                + "            selectedMethod = method;\n"
                + "            renderCallChart();\n"
                + "        }\n"
                + "        \n"
                + "        // ✅ 核心修正：渲染接口调用趋势图表，严格过滤非选中方法\n"
                + "        function renderCallChart() {\n"
                + "            if (currentData.length === 0) {\n"
                + "                callChart.setOption({title: {text: '暂无接口调用数据',left:'center'}});\n"
                + "                return;\n"
                + "            }\n"
                + "            const allTimes = currentData.map(item => item.time);\n"
                + "            const allMethodNames = new Set();\n"
                + "            currentData.forEach(item => Object.keys(item.methodCountMap).forEach(m => allMethodNames.add(m)));\n"
                + "            const methodList = Array.from(allMethodNames);\n"
                + "            \n"
                + "            // 1. 初始化系列数据，只保留【总调用量】\n"
                + "            const seriesData = [{\n"
                + "                name: '总调用量',\n"
                + "                type: 'line',\n"
                + "                data: currentData.map(item => item.totalCount),\n"
                + "                smooth: true,\n"
                + "                lineWidth: 3,\n"
                + "                color: '#ff4d4f',\n"
                + "                itemStyle: {color: '#ff4d4f'}\n"
                + "            }];\n"
                + "            \n"
                + "            const colors = ['#1677ff', '#36cbcb', '#722ed1', '#fa8c16', '#52c41a', '#ffc53d'];\n"
                + "            \n"
                + "            // 2. 严格控制折线生成逻辑\n"
                + "            if (selectedMethod === 'all') {\n"
                + "                // 展示全部方法：循环添加所有方法的折线\n"
                + "                methodList.forEach((method, index) => {\n"
                + "                    seriesData.push({\n"
                + "                        name: method,\n"
                + "                        type: 'line',\n"
                + "                        smooth: true,\n"
                + "                        lineWidth:2,\n"
                + "                        data: currentData.map(item => item.methodCountMap[method] || 0),\n"
                + "                        color: colors[index % colors.length]\n"
                + "                    });\n"
                + "                });\n"
                + "            } else {\n"
                + "                // ✅ 关键修正：只添加【选中的单个方法】，其他方法完全不参与\n"
                + "                if (methodList.includes(selectedMethod)) {\n"
                + "                    seriesData.push({\n"
                + "                        name: selectedMethod,\n"
                + "                        type: 'line',\n"
                + "                        smooth: true,\n"
                + "                        lineWidth:2,\n"
                + "                        data: currentData.map(item => item.methodCountMap[selectedMethod] || 0),\n"
                + "                        color: '#1677ff',\n"
                + "                        itemStyle: {color: '#1677ff'}\n"
                + "                    });\n"
                + "                }\n"
                + "            }\n"
                + "            \n"
                + "            // 3. 图例数据严格对应系列数据，不额外添加\n"
                + "            const legendData = seriesData.map(s => s.name);\n"
                + "            \n"
                + "            const option = {\n"
                + "                title: { text: `接口调用趋势(${currentUnit === 'second'?'秒级':'分钟级'}) 【${selectedMethod==='all'?'全部方法':selectedMethod}】`, left: 'center' },\n"
                + "                tooltip: {\n"
                + "                    trigger: 'axis',\n"
                + "                    formatter: function(params) {\n"
                + "                        let res = params[0].name;\n"
                + "                        const targetItem = currentData.find(item => item.time === params[0].name);\n"
                + "                        params.forEach(item => res += '<br/>' + item.seriesName + ': ' + item.value);\n"
                + "                        const successRate = targetItem?.successRate || 0;\n"
                + "                        res += '<br/>成功率: ' + successRate.toFixed(2) + '%';\n"
                + "                        return res;\n"
                + "                    }\n"
                + "                },\n"
                + "                legend: { data: legendData, top: 30 },\n"
                + "                grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },\n"
                + "                xAxis: { type: 'category', data: allTimes, axisLabel: { rotate: 30 } },\n"
                + "                yAxis: { type: 'value', name: '调用量', min: 0 },\n"
                + "                series: seriesData\n"
                + "            };\n"
                + "            // 4. 强制清空之前的图表配置，避免残留\n"
                + "            callChart.setOption(option, true);\n"
                + "        }\n"
                + "        \n"
                + "        // 渲染CPU+JVM内存趋势图表\n"
                + "        function renderCpuMemChart() {\n"
                + "            if (currentCpuMemData.length === 0) {\n"
                + "                cpuMemChart.setOption({title: {text: '暂无CPU/内存使用率数据',left:'center'}});\n"
                + "                return;\n"
                + "            }\n"
                + "            const allTimes = currentCpuMemData.map(item => item.time);\n"
                + "            const cpuData = currentCpuMemData.map(item => item.cpuUsage);\n"
                + "            const memData = currentCpuMemData.map(item => item.memoryUsage);\n"
                + "            \n"
                + "            const option = {\n"
                + "                title: { text: `CPU使用率(%) & JVM内存使用率(%) (${currentUnit === 'second'?'秒级':'分钟级'})`, left: 'center' },\n"
                + "                tooltip: {\n"
                + "                    trigger: 'axis',\n"
                + "                    formatter: function(params) {\n"
                + "                        let res = params[0].name;\n"
                + "                        params.forEach(item => res += '<br/>' + item.seriesName + ': ' + item.value + '%');\n"
                + "                        return res;\n"
                + "                    }\n"
                + "                },\n"
                + "                legend: { data: ['CPU使用率', 'JVM内存使用率'], top: 30 },\n"
                + "                grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },\n"
                + "                xAxis: { type: 'category', data: allTimes, axisLabel: { rotate: 30 } },\n"
                + "                yAxis: { type: 'value', name: '使用率(%)', min: 0, max: 100 },\n"
                + "                series: [\n"
                + "                    {name: 'CPU使用率',type: 'line',data: cpuData,smooth: true,lineWidth:2,color: '#ff7a45',itemStyle: { color: '#ff7a45' }},\n"
                + "                    {name: 'JVM内存使用率',type: 'line',data: memData,smooth: true,lineWidth:2,color: '#36cbcb',itemStyle: { color: '#36cbcb' }}\n"
                + "                ]\n"
                + "            };\n"
                + "            cpuMemChart.setOption(option, true);\n"
                + "        }\n"
                + "        \n"
                + "        // 自适应窗口大小\n"
                + "        window.addEventListener('resize', () => {\n"
                + "            callChart.resize();\n"
                + "            cpuMemChart.resize();\n"
                + "        });\n"
                + "    </script>\n"
                + "</body>\n"
                + "</html>";
    }

    public static void main(String[] args) throws IOException {
        String rawJson = "{\"allResults\":{\"TestService-provider\":{\"count\":20,\"provideResultList\":[{\"endTime\":\"2026-01-03 12:52:32.656\",\"methodName\":\"sayHello\",\"serviceName\":\"TestService-provider\",\"startTime\":\"2026-01-03 12:52:32.605\",\"success\":true},{\"endTime\":\"2026-01-03 12:52:32.77\",\"methodName\":\"sayHello2\",\"serviceName\":\"TestService-provider\",\"startTime\":\"2026-01-03 12:52:32.77\",\"success\":true}]}},\"cpuEndTime\":\"2026-01-03 12:52:36.407\",\"cpuStartTime\":\"2026-01-03 12:52:26.475\",\"cpuUsage\":{1:100,2:4,3:2,4:2,5:100,6:18,7:47},\"memoryUsage\":{1:1,2:1,3:1,4:1,5:2,6:2,7:2}}";
        generateCallTrendHtml(rawJson, "./service_call_trend_dynamic.html");
    }
}