package com.delivery.tracking_service.payload;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseResponse<T> {
    private final int status;
    private final String message;
    private final T data;

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
}
