package com.delivery.notification_service.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ✅ Base Response wrapper cho tất cả API responses theo Backend Instructions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    
    private int status;
    private T data;
    private String message;
    
    public BaseResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.message = status == 1 ? "Success" : "Failed";
    }
}
