package com.delivery.user_service.dto;

import lombok.Data;

@Data
public class AuthServiceResponse<T> {
    private int status;
    private String message;
    private T data;
}
