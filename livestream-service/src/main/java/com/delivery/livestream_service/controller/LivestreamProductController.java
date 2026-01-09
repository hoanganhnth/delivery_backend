package com.delivery.livestream_service.controller;

import com.delivery.livestream_service.common.constants.ApiPathConstants;
import com.delivery.livestream_service.common.constants.HttpHeaderConstants;
import com.delivery.livestream_service.dto.request.PinProductRequest;
import com.delivery.livestream_service.dto.response.LivestreamProductResponse;
import com.delivery.livestream_service.payload.BaseResponse;
import com.delivery.livestream_service.service.LivestreamProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(ApiPathConstants.LIVESTREAMS)
public class LivestreamProductController {

    private final LivestreamProductService productService;

    public LivestreamProductController(LivestreamProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/{id}/products/pin")
    public ResponseEntity<BaseResponse<LivestreamProductResponse>> pinProduct(
            @PathVariable UUID id,
            @Valid @RequestBody PinProductRequest request,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        LivestreamProductResponse response = productService.pinProduct(id, request, userId);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Pin sản phẩm thành công"));
    }

    @DeleteMapping("/{id}/products/{productId}/pin")
    public ResponseEntity<BaseResponse<Void>> unpinProduct(
            @PathVariable UUID id,
            @PathVariable Long productId,
            @RequestHeader(value = HttpHeaderConstants.X_USER_ID) Long userId) {
        productService.unpinProduct(id, productId, userId);
        return ResponseEntity.ok(new BaseResponse<>(1, null, "Bỏ pin sản phẩm thành công"));
    }

    @GetMapping("/{id}/products")
    public ResponseEntity<BaseResponse<List<LivestreamProductResponse>>> getProductsByLivestream(
            @PathVariable UUID id) {
        List<LivestreamProductResponse> response = productService.getProductsByLivestream(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy danh sách sản phẩm thành công"));
    }

    @GetMapping("/{id}/products/pinned")
    public ResponseEntity<BaseResponse<List<LivestreamProductResponse>>> getPinnedProducts(
            @PathVariable UUID id) {
        List<LivestreamProductResponse> response = productService.getPinnedProducts(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response, "Lấy sản phẩm đang pin thành công"));
    }
}
