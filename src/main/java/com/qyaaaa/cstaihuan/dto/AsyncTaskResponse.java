package com.qyaaaa.cstaihuan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskResponse {
    private String taskId;
    private String type;
    private String status;
    private int progress;
    private Integer current;
    private Integer total;
    private String message;
    private boolean canContinue;
    private String errorCode;
    private String errorMessage;
    private Object result;
    private long createdAt;
    private Long startedAt;
    private Long finishedAt;
}
