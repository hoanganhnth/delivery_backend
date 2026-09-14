package com.delivery.livestream_service.payload;

import com.fasterxml.jackson.annotation.JsonInclude;

public class BaseResponse<T> {
    private final int status;      // 1 = success, 0 = failure
    private final String message;  // Success/error message
    private final T data;          // Response data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final ErrorPayload error;
    
    public BaseResponse(int status, T data, String message) {
        this(status, data, message, null);
    }

    public BaseResponse(int status, T data, String message, ErrorPayload error) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.error = error;
    }
    
    public BaseResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.message = status == 1 ? "Thành công" : "Thất bại";
        this.error = null;
    }
    
    public int getStatus() {
        return status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public T getData() {
        return data;
    }

    public ErrorPayload getError() {
        return error;
    }

    public record ErrorPayload(String code, Object details) {
        public ErrorPayload(String code) {
            this(code, null);
        }
    }
}
