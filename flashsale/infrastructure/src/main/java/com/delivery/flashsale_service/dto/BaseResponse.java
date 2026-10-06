package com.delivery.flashsale_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> {
    private int status;
    private T data;
    private String message;

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(1, data, "Success");
    }

    public static <T> BaseResponse<T> success(T data, String message) {
        return new BaseResponse<>(1, data, message);
    }

    public static <T> BaseResponse<T> failure(String message) {
        return new BaseResponse<>(0, null, message);
    }
}
