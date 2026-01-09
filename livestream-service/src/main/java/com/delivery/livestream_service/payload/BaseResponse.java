package com.delivery.livestream_service.payload;

public class BaseResponse<T> {
    private final int status;      // 1 = success, 0 = failure
    private final String message;  // Success/error message
    private final T data;          // Response data
    
    public BaseResponse(int status, T data, String message) {
        this.status = status;
        this.message = message;
        this.data = data;
    }
    
    public BaseResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.message = status == 1 ? "Thành công" : "Thất bại";
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
}
