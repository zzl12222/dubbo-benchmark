package com.dubbo.common.entry;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;
@Data
public class QoPData implements Serializable {
    private String providerId;
    private Long timestamp;
    private Double avgResponseTime;
    private Double p95ResponseTime;
    private Double p99ResponseTime;
    private Double successRate;
    private Integer qps;
    private Integer concurrentCalls;
    private Map<String, Object> customMetrics;
}
