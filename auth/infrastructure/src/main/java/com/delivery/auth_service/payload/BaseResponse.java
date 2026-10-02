package com.delivery.auth_service.payload;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BaseResponse<T> {
    private int status;      // 1 = success, 0 = failure
    private String message;  // Success/error message
    private T data;          // Response data
    
    public BaseResponse(int status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int status, T data) {
        this.status = status;
        this.data = data;
        this.message = status == 1 ? "Thành công" : "Thất bại";
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(1, data);
    }

    public static <T> BaseResponse<T> success(T data, String message) {
        return new BaseResponse<>(1, data, message);
    }

    public static <T> BaseResponse<T> failure(String message) {
        return new BaseResponse<>(0, null, message);
    }

    public static <T> BaseResponse<T> failure(T data, String message) {
        return new BaseResponse<>(0, data, message);
    }
}
