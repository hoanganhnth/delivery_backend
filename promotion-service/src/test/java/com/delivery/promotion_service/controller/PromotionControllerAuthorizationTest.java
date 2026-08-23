package com.delivery.promotion_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
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
import java.util.Set;
import java.util.UUID;

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
        AuthenticatedActor userActor = new AuthenticatedActor(7L, "user@example.com", Set.of("USER"));
        assertThrows(ResponseStatusException.class,
                () -> controller.createPlatformVoucher(new CreateVoucherRequest(), userActor));
    }

    @Test
    void customerVoucherWalletRejectsNonUserRoles() {
        AuthenticatedActor shipperActor = new AuthenticatedActor(7L, "shipper@example.com", Set.of("SHIPPER"));
        AuthenticatedActor shopActor = new AuthenticatedActor(7L, "shop@example.com", Set.of("SHOP_OWNER"));

        assertThrows(ResponseStatusException.class,
                () -> controller.collectVoucher("WELCOME", shipperActor));
        assertThrows(ResponseStatusException.class,
                () -> controller.getMyVouchers(shopActor));

        verifyNoInteractions(promotionService);
    }

    @Test
    void customerVoucherWalletAcceptsUserRole() {
        AuthenticatedActor userActor = new AuthenticatedActor(7L, "user@example.com", Set.of("USER"));
        when(promotionService.getCollectedVouchers(7L)).thenReturn(List.of());

        var collectResponse = controller.collectVoucher("WELCOME", userActor);
        var walletResponse = controller.getMyVouchers(userActor);

        verify(promotionService).collectVoucher(7L, "WELCOME");
        verify(promotionService).getCollectedVouchers(7L);
        assertEquals(1, collectResponse.getBody().getStatus());
        assertEquals("Collected successfully", collectResponse.getBody().getData());
        assertEquals(1, walletResponse.getBody().getStatus());
        assertEquals(List.of(), walletResponse.getBody().getData());
    }

    @Test
    void capabilityIsEnabledOnlyForConfiguredStablePrincipal() {
        ReflectionTestUtils.setField(controller, "stackingEnabled", true);
        ReflectionTestUtils.setField(controller, "checkoutEnabled", true);
        ReflectionTestUtils.setField(controller, "stackingCanaryPrincipals", "42,99");
        AuthenticatedActor canary = new AuthenticatedActor(42L, "user@example.com", Set.of("USER"));
        AuthenticatedActor control = new AuthenticatedActor(7L, "control@example.com", Set.of("USER"));

        assertEquals(true, controller.capability(canary).getBody().getData().enabled());
        assertEquals(false, controller.capability(control).getBody().getData().enabled());
        assertEquals(3, controller.capability(canary).getBody().getData().maxVouchers());
    }

    @Test
    void adminSurfacesUseCanonicalSuccessEnvelope() {
        AuthenticatedActor adminActor = new AuthenticatedActor(7L, "admin@example.com", Set.of("ADMIN"));
        Voucher voucher = new Voucher();
        voucher.setId(11L);
        when(promotionService.createVoucher(org.mockito.ArgumentMatchers.any())).thenReturn(voucher);
        when(promotionService.listAllVouchers()).thenReturn(List.of(voucher));

        var createResponse = controller.createPlatformVoucher(new CreateVoucherRequest(), adminActor);
        var listResponse = controller.listAllVouchers(adminActor);
        var deleteResponse = controller.deleteVoucher(11L, adminActor);

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
    void calculateRequiresInternalCredentialBeforeCheckoutIsEnabled() {
        CartContextRequest request = new CartContextRequest();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.calculate(request, null));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(promotionService);
    }

    @Test
    void calculateStaysClosedUntilRecoveryIsProven() {
        CartContextRequest request = new CartContextRequest();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.calculate(request, "test-secret"));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verifyNoInteractions(promotionService);
    }

    @Test
    void enabledCalculateUsesTrustedInternalUserId() {
        ReflectionTestUtils.setField(controller, "checkoutEnabled", true);
        CartContextRequest request = new CartContextRequest();
        request.setShopId(3L);
        request.setSubTotal(java.math.BigDecimal.TEN);
        request.setShippingFee(java.math.BigDecimal.ZERO);
        request.setUserId(999L);

        controller.calculate(request, "test-secret");

        assertEquals(999L, request.getUserId());
        verify(promotionService).calculate(request);
    }
}
