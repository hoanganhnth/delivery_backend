package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.RefundCaseResponse;
import com.delivery.settlement_service.entity.RefundCase.RefundStatus;
import com.delivery.settlement_service.service.RefundCaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundAdminControllerTest {
    @Mock RefundCaseService refundCaseService;

    @Test
    void nonAdminCannotReadRefundQueue() {
        RefundAdminController controller = new RefundAdminController(refundCaseService);

        var response = controller.list("USER", null, 100);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(refundCaseService, never()).listAdminCases(org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    void adminCanFilterQueueWithoutMutationSurface() {
        RefundAdminController controller = new RefundAdminController(refundCaseService);
        when(refundCaseService.listAdminCases(RefundStatus.MANUAL_REVIEW, 25))
                .thenReturn(List.of(RefundCaseResponse.builder().status("MANUAL_REVIEW").build()));

        var response = controller.list("ADMIN", "manual_review", 25);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
        verify(refundCaseService).listAdminCases(RefundStatus.MANUAL_REVIEW, 25);
    }

    @Test
    void unknownStatusIsRejectedBeforeQuery() {
        RefundAdminController controller = new RefundAdminController(refundCaseService);

        var response = controller.list("ADMIN", "provider_unknown", 25);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Unknown refund status");
        verify(refundCaseService, never()).listAdminCases(org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    void nonAdminCannotReadSingleCase() {
        RefundAdminController controller = new RefundAdminController(refundCaseService);

        var response = controller.get("SHOP_OWNER", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(refundCaseService, never()).getAdminCase(org.mockito.ArgumentMatchers.any());
    }
}
