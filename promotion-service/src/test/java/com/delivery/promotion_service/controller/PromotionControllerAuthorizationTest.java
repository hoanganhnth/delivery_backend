package com.delivery.promotion_service.controller;

import com.delivery.promotion_service.dto.CartContextRequest;
import com.delivery.promotion_service.dto.CreateVoucherRequest;
import com.delivery.promotion_service.dto.ReserveRequest;
import com.delivery.promotion_service.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.delivery.promotion_service.entity.Voucher;

@ExtendWith(MockitoExtension.class)
class PromotionControllerAuthorizationTest {

    @Mock
    private PromotionService promotionService;
    private Validator validator;

    private PromotionController controller;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        controller = new PromotionController(promotionService, validator);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
        ReflectionTestUtils.setField(controller, "checkoutEnabled", false);
    }

    @Test
    void platformVoucherRequiresAdmin() {
        assertThrows(ResponseStatusException.class,
                () -> controller.createPlatformVoucher(new CreateVoucherRequest(), "USER"));
    }

    @Test
    void customerVoucherWalletRejectsNonUserRoles() {
        assertThrows(ResponseStatusException.class,
                () -> controller.collectVoucher(7L, "SHIPPER", "WELCOME"));
        assertThrows(ResponseStatusException.class,
                () -> controller.getMyVouchers(7L, "SHOP_OWNER"));

        verifyNoInteractions(promotionService);
    }

    @Test
    void customerVoucherWalletAcceptsUserRole() {
        when(promotionService.getCollectedVouchers(7L)).thenReturn(List.of());

        var collectResponse = controller.collectVoucher(7L, "USER", "WELCOME");
        var walletResponse = controller.getMyVouchers(7L, "USER");

        verify(promotionService).collectVoucher(7L, "WELCOME");
        verify(promotionService).getCollectedVouchers(7L);
        assertEquals(1, collectResponse.getBody().getStatus());
        assertEquals("Collected successfully", collectResponse.getBody().getData());
        assertEquals(1, walletResponse.getBody().getStatus());
        assertEquals(List.of(), walletResponse.getBody().getData());
    }

    @Test
    void adminSurfacesUseCanonicalSuccessEnvelope() {
        Voucher voucher = new Voucher();
        voucher.setId(11L);
        when(promotionService.createVoucher(org.mockito.ArgumentMatchers.any())).thenReturn(voucher);
        when(promotionService.listAllVouchers()).thenReturn(List.of(voucher));

        var createResponse = controller.createPlatformVoucher(new CreateVoucherRequest(), "ADMIN");
        var listResponse = controller.listAllVouchers("ADMIN");
        var deleteResponse = controller.deleteVoucher(11L, "ADMIN");

        assertEquals(1, createResponse.getBody().getStatus());
        assertEquals(voucher.getId(), createResponse.getBody().getData().id());
        assertEquals(1, listResponse.getBody().getStatus());
        assertEquals(List.of(voucher.getId()), listResponse.getBody().getData().stream()
                .map(com.delivery.promotion_service.dto.VoucherResponse::id)
                .toList());
        assertEquals(1, deleteResponse.getBody().getStatus());
        assertEquals(null, deleteResponse.getBody().getData());
    }

    @Test
    void reserveRequiresInternalCredential() {
        assertThrows(ResponseStatusException.class,
                () -> controller.reserve(new ReserveRequest(), "wrong-secret"));
    }

    @Test
    void reserveStaysClosedUntilRecoveryIsProven() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.reserve(new ReserveRequest(), "test-secret"));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode());
        verifyNoInteractions(promotionService);
    }

    @Test
    void reserveRejectsInvalidRequestAfterCheckoutIsEnabled() {
        ReflectionTestUtils.setField(controller, "checkoutEnabled", true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.reserve(new ReserveRequest(), "test-secret"));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(promotionService);
    }

    @Test
    void calculateStaysClosedUntilRecoveryIsProven() {
        CartContextRequest request = new CartContextRequest();
        request.setUserId(999L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.calculate(request, 7L, "USER"));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertEquals(999L, request.getUserId());
        verifyNoInteractions(promotionService);
    }

    @Test
    void calculateRequiresUserRoleBeforeCheckoutFlag() {
        CartContextRequest request = new CartContextRequest();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.calculate(request, 7L, "SHOP_OWNER"));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(promotionService);
    }

    @Test
    void enabledCalculateOverridesBodyUserWithGatewayIdentity() {
        ReflectionTestUtils.setField(controller, "checkoutEnabled", true);
        CartContextRequest request = new CartContextRequest();
        request.setUserId(999L);

        controller.calculate(request, 7L, "USER");

        assertEquals(7L, request.getUserId());
        verify(promotionService).calculate(request);
    }
}
