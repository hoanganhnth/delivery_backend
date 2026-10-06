package com.delivery.search_service.controller;

import com.delivery.search_service.dto.DishSearchResponse;
import com.delivery.search_service.dto.RestaurantSearchResponse;
import com.delivery.search_service.service.SearchService;
import com.delivery.search_service.payload.BaseResponse;
import com.delivery.search_service.payload.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/restaurants")
    public ResponseEntity<BaseResponse<PageResponse<RestaurantSearchResponse>>> searchRestaurants(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(new BaseResponse<>(
                1,
                PageResponse.from(searchService.searchRestaurants(
                        q.trim(), PageRequest.of(page, size))
                        .map(RestaurantSearchResponse::from))));
    }

    @GetMapping("/dishes")
    public ResponseEntity<BaseResponse<PageResponse<DishSearchResponse>>> searchDishes(
            @RequestParam @NotBlank @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(new BaseResponse<>(
                1,
                PageResponse.from(searchService.searchDishes(
                        q.trim(), PageRequest.of(page, size))
                        .map(DishSearchResponse::from))));
    }

}
