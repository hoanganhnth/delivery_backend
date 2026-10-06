package com.delivery.livestream_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class TokenResponse {

    private String token;
    private String roomId;
    private UUID livestreamId;
    private LocalDateTime expiresAt;
}
