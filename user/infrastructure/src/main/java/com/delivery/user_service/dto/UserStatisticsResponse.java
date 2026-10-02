package com.delivery.user_service.dto;

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
public class UserStatisticsResponse {

    private Long totalUsers;

    private Long userCount;

    private Long adminCount;

    private Long shipperCount;

    private Long shopOwnerCount;

    private Long activeUsers;

    private Long blockedUsers;
}
