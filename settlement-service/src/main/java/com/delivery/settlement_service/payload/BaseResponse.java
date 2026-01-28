package com.delivery.settlement_service.payload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    private int status; // 0 = error, 1 = success
    private String message;
    private T data;

    public BaseResponse(int status, T data) {
        this.status = status;
        this.data = data;
    }
}
