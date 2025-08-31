package com.delivery.auth_service.payload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor              // 👈 thêm constructor rỗng
@AllArgsConstructor  
public class BaseResponse<T> {
    private  int status;      // 1 = success, 0 = failure
    private  String message;  // Success/error message
    private  T data;          // Response data
    
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
