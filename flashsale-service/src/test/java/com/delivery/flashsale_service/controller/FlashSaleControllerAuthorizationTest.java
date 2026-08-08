package com.delivery.flashsale_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.flashsale_service.dto.RegisterItemRequest;
import com.delivery.flashsale_service.service.FlashSaleService;
import com.delivery.flashsale_service.service.FlashSaleStockService;
import com.delivery.flashsale_service.client.RestaurantOwnershipClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.delivery.flashsale_service.dto.FlashSaleReservationRequest;
import com.delivery.flashsale_service.dto.ReserveItemRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlashSaleControllerAuthorizationTest {

    @Mock FlashSaleService flashSaleService;
    @Mock FlashSaleStockService stockService;
    @Mock ObjectProvider<FlashSaleStockService> stockServiceProvider;
    @Mock RestaurantOwnershipClient ownershipClient;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void adminControllerRejectsNonAdmin() {
        AdminFlashSaleController controller = new AdminFlashSaleController(flashSaleService);
        AuthenticatedActor shopActor = new AuthenticatedActor(7L, "shop@example.com", Set.of("SHOP_OWNER"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getAllCampaigns(shopActor));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(flashSaleService);
    }

    @Test
    void publicAndAdminSuccessUseCanonicalEnvelopeStatus() {
        AdminFlashSaleController admin = new AdminFlashSaleController(flashSaleService);
        PublicFlashSaleController publicController = new PublicFlashSaleController(flashSaleService);
        AuthenticatedActor adminActor = new AuthenticatedActor(7L, "admin@example.com", Set.of("ADMIN"));

        var adminResponse = admin.getAllCampaigns(adminActor);
        var publicResponse = publicController.getActiveCampaigns();

        assertEquals(1, adminResponse.getBody().getStatus());
        assertEquals(1, publicResponse.getBody().getStatus());
    }

    @Test
    void merchantControllerRejectsNonOwner() {
        MerchantFlashSaleController controller = new MerchantFlashSaleController(flashSaleService, ownershipClient);
        AuthenticatedActor userActor = new AuthenticatedActor(7L, "user@example.com", Set.of("USER"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.registerItem(new RegisterItemRequest(), userActor));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(flashSaleService);
        verifyNoInteractions(ownershipClient);
    }

    @Test
    void merchantIdentityIsUsedForRestaurantOwnershipCheck() {
        MerchantFlashSaleController controller = new MerchantFlashSaleController(flashSaleService, ownershipClient);
        RegisterItemRequest request = new RegisterItemRequest();
        request.setRestaurantId(9L);
        AuthenticatedActor shopActor = new AuthenticatedActor(21L, "shop@example.com", Set.of("SHOP_OWNER"));

        controller.registerItem(request, shopActor);

        verify(ownershipClient).requireOwnedBy(9L, 21L);
        verify(flashSaleService).registerItem(request);
    }

    @Test
    void internalReserveFailsClosedWithoutMatchingSecret() {
        InternalFlashSaleController controller = new InternalFlashSaleController(stockServiceProvider, validator);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
        ReflectionTestUtils.setField(controller, "checkoutEnabled", false);

        var missing = controller.reserveStock(null, null);
        var wrong = controller.reserveStock(null, "wrong-secret");

        assertEquals(HttpStatus.FORBIDDEN, missing.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, wrong.getStatusCode());
        verifyNoInteractions(stockServiceProvider, stockService);
    }

    @Test
    void internalReserveStaysClosedUntilRecoveryIsProven() {
        InternalFlashSaleController controller = new InternalFlashSaleController(stockServiceProvider, validator);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
        ReflectionTestUtils.setField(controller, "checkoutEnabled", false);

        var response = controller.reserveStock(null, "test-secret");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(0, response.getBody().getStatus());
        verifyNoInteractions(stockServiceProvider, stockService);
    }

    @Test
    void internalReserveValidatesItemsAfterCheckoutIsEnabled() {
        InternalFlashSaleController controller = new InternalFlashSaleController(stockServiceProvider, validator);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
        ReflectionTestUtils.setField(controller, "checkoutEnabled", true);

        var response = controller.reserveStock(new FlashSaleReservationRequest(), "test-secret");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(stockServiceProvider, stockService);
    }

    @Test
    void enabledReserveFailsClosedWhenStockGraphIsUnavailable() {
        InternalFlashSaleController controller = new InternalFlashSaleController(stockServiceProvider, validator);
        ReflectionTestUtils.setField(controller, "internalSecret", "test-secret");
        ReflectionTestUtils.setField(controller, "checkoutEnabled", true);
        var item = new ReserveItemRequest();
        item.setFlashSaleItemId(1L);
        item.setQuantity(1);
        var request = new FlashSaleReservationRequest();
        request.setReservationId(UUID.randomUUID());
        request.setOrderId(2L);
        request.setUserId(3L);
        request.setRestaurantId(4L);
        request.setItems(List.of(item));
        when(stockServiceProvider.getIfAvailable()).thenReturn(null);

        var response = controller.reserveStock(request, "test-secret");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        verify(stockServiceProvider).getIfAvailable();
        verifyNoInteractions(stockService);
    }
}
