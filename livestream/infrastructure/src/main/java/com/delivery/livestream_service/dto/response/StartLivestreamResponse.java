package com.delivery.livestream_service.dto.response;

import com.delivery.livestream_service.enums.LivestreamStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response khi start/join livestream - bao gồm token để join Agora
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartLivestreamResponse {
    
    // Thông tin livestream
    private UUID livestreamId;
    private String channelName;
    private LivestreamStatus status;
    
    // Thông tin token để join Agora
    private String token;
    private Integer uid;
    private String role; // HOST hoặc VIEWER
    private LocalDateTime tokenExpiresAt;
    
    // Metadata
    private String title;
    private Long restaurantId;
    private LocalDateTime startedAt;
}
