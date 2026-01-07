package com.dubbo.common.entry;

import lombok.Data;

import java.util.Map;

@Data
public class TestResultFileDTO {
    private String testId;
    private Long startTime;
    private Long endTime;
    private Integer totalRequests;
    private Integer successfulRequests;
    private Integer failedRequests;
    private Map<String, Integer> providerDistribution;
}