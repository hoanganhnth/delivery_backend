package com.delivery.livestream_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response khi viewer join livestream
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoinLivestreamResponse {
    
    // Thông tin livestream
    private UUID livestreamId;
    private String channelName;
    private String title;
    private Long restaurantId;
    
    // Thông tin token để join Agora
    private String token;
    private Integer uid;
    private LocalDateTime tokenExpiresAt;
    
    // Metadata
    private Long sellerId;
    private LocalDateTime startedAt;
    private Integer currentViewers; // Số viewer hiện tại (optional)
}
