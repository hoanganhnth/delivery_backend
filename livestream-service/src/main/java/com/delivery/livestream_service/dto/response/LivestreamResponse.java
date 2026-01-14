package com.delivery.livestream_service.dto.response;

import com.delivery.livestream_service.enums.LivestreamStatus;
import com.delivery.livestream_service.enums.StreamProvider;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LivestreamResponse {

    private UUID id;
    private Long sellerId;
    private Long restaurantId;
    private String title;
    private String description;
    private LivestreamStatus status;
    private StreamProvider streamProvider;
    private String roomId;
    private String channelName;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LivestreamProductResponse> pinnedProducts;
}
