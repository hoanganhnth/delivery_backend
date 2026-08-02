package com.delivery.settlement_service.controller;

import com.delivery.settlement_service.dto.response.RefundCustomerCaseResponse;
import com.delivery.settlement_service.service.RefundCaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundCustomerControllerTest {
    @Mock RefundCaseService refundCaseService;

    @Test
    void nonUserCannotReadRefundStatus() {
        RefundCustomerController controller = new RefundCustomerController(refundCaseService);

        var response = controller.list("SHOP_OWNER", 7L, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(refundCaseService, never()).listCustomerCases(anyLong(), anyInt());
    }

    @Test
    void missingTrustedUserIdentityIsRejected() {
        RefundCustomerController controller = new RefundCustomerController(refundCaseService);

        var response = controller.list("USER", null, 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(refundCaseService, never()).listCustomerCases(anyLong(), anyInt());
    }

    @Test
    void userReadsOnlyOwnRefundCases() {
        RefundCustomerController controller = new RefundCustomerController(refundCaseService);
        RefundCustomerCaseResponse refund = RefundCustomerCaseResponse.builder()
                .orderId(101L)
                .status("MANUAL_REVIEW")
                .build();
        when(refundCaseService.listCustomerCases(7L, 25)).thenReturn(List.of(refund));

        var response = controller.list("USER", 7L, 25);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).containsExactly(refund);
        verify(refundCaseService).listCustomerCases(7L, 25);
    }

    @Test
    void controllerDeclaresNoRefundMutationMapping() {
        Set<Class<? extends Annotation>> mutationMappings = Set.of(
                PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

        assertThat(Arrays.stream(RefundCustomerController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getAnnotations()))
                .map(Annotation::annotationType))
                .noneMatch(mutationMappings::contains);
    }
}
