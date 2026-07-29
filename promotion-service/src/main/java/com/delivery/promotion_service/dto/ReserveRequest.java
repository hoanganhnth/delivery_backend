package com.delivery.promotion_service.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveRequest {
    @NotNull
    @Positive
    private Long userId;
    @NotNull
    @Positive
    private Long orderId;
    @NotEmpty
    private List<@NotNull @Positive Long> voucherIds;
}
