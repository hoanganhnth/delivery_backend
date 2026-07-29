package com.delivery.user_service.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class BlockUserRequest {

    @Size(max = 500)
    private String reason;
}
