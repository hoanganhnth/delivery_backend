package com.delivery.auth_service.dto;

public record AuthAccountDto(
    Long id,
    String email,
    String role
) {}