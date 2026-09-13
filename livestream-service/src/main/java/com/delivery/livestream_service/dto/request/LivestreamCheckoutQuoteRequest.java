package com.delivery.livestream_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class LivestreamCheckoutQuoteRequest {
    @NotNull
    private UUID livestreamId;

    @NotNull
    @Positive
    private Long restaurantId;

    @NotEmpty
    @Size(max = 50)
    private List<@NotNull @Positive Long> productIds;
}
