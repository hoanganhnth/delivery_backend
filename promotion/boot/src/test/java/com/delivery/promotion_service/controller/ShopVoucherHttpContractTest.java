package com.delivery.promotion_service.controller;

import com.delivery.auth.resourceserver.security.AuthenticatedActor;
import com.delivery.promotion_service.entity.Voucher;
import com.delivery.promotion_service.service.PromotionService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopVoucherHttpContractTest {
    @Test void shopRequestDerivesScopeBeforeValidation() throws Exception {
        var service = mock(PromotionService.class);
        when(service.createShopVoucher(any(), anyLong(), anyLong())).thenReturn(Voucher.builder().id(9L).build());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var mvc = MockMvcBuilders.standaloneSetup(new PromotionController(service, factory.getValidator()))
                    .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                        public boolean supportsParameter(MethodParameter parameter) {
                            return parameter.getParameterType() == AuthenticatedActor.class;
                        }
                        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                NativeWebRequest request, WebDataBinderFactory binder) {
                            return new AuthenticatedActor(159L, "owner@test.dev", Set.of("SHOP_OWNER"));
                        }
                    }).build();
            mvc.perform(post("/api/promotions/shop").contentType("application/json").content("""
                    {"code":"SHOP10","name":"Shop discount","restaurantId":36,
                     "rewardType":"FIXED","discountValue":10,"totalQuantity":10,
                     "usageLimitPerUser":1,"minOrderValue":0,
                     "startTime":"2026-01-01T00:00:00","endTime":"2099-01-01T00:00:00"}
                    """)).andExpect(status().isOk());
            verify(service).createShopVoucher(argThat(request -> request.getScopeType() == Voucher.ScopeType.SHOP
                    && Long.valueOf(36L).equals(request.getScopeRefId())), anyLong(), anyLong());
            mvc.perform(post("/api/promotions/shop").contentType("application/json")
                    .content("{\"restaurantId\":36}")).andExpect(status().isBadRequest());
        }
    }
}
