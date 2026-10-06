package com.delivery.search_service.payload;

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
        this(status, data, status == 1 ? "Thành công" : "Thất bại");
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
