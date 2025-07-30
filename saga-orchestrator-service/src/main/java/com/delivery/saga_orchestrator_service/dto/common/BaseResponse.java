package com.delivery.saga_orchestrator_service.dto.common;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseResponse<T> {
    private int status;      // 1 = success, 0 = failure
    private String message;  // Success/error message
    private T data;          // Response data
}
