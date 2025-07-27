package com.delivery.saga_orchestrator_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class CreateProfileRequest {
    private Long authId;
    private String email;
    private String role;

}
