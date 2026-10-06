package com.delivery.livestream_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class RenewLivestreamTokenResponse {
    private UUID livestreamId;
    private String channelName;
    private String token;
    private int uid;
    private String role;
    private LocalDateTime tokenExpiresAt;
}
