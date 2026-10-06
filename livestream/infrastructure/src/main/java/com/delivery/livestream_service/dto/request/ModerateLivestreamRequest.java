package com.delivery.livestream_service.dto.request;

import com.delivery.livestream_service.enums.LivestreamModerationAction;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ModerateLivestreamRequest(
        @NotNull LivestreamModerationAction action,
        @NotBlank @Size(max = 1000) String reason,
        @Positive Long productId) {

    @AssertTrue(message = "productId is required only for UNPIN")
    public boolean isProductTargetValid() {
        return action == LivestreamModerationAction.UNPIN ? productId != null : productId == null;
    }
}
