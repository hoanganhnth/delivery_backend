package com.delivery.livestream_service.dto.request;

import com.delivery.livestream_service.enums.TokenRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateTokenRequest {

    @NotNull(message = "Role không được để trống")
    private TokenRole role;

    private Integer expireSeconds = 3600; // Default 1 hour
}
