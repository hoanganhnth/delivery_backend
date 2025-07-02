package com.delivery.restaurant_service.controller;

import com.delivery.restaurant_service.common.constants.ApiPathConstants;
import com.delivery.restaurant_service.common.constants.HttpHeaderConstants;
import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.payload.BaseResponse;
import com.delivery.restaurant_service.service.RestaurantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(ApiPathConstants.RESTAURANTS)
public class RestaurantController {

    @Autowired
    private RestaurantService restaurantService;

    @PostMapping
    public ResponseEntity<BaseResponse<RestaurantResponse>> create(@RequestBody CreateRestaurantRequest request,
                                                                   @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId,
                                                                   @RequestHeader(value = HttpHeaderConstants.X_ROLE, required = false) String role) {
        RestaurantResponse response = restaurantService.createRestaurant(request, creatorId, role);

        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<RestaurantResponse>> update(@PathVariable Long id,
                                                                   @RequestBody UpdateRestaurantRequest request,
                                                                   @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId) {
        RestaurantResponse response = restaurantService.updateRestaurant(id, request, creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long id,
                                                     @RequestHeader(value = HttpHeaderConstants.X_USER_ID, required = false) Long creatorId) {

        restaurantService.deleteRestaurant(id, creatorId);
        return ResponseEntity.ok(new BaseResponse<>(1, null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<RestaurantResponse>> getById(@PathVariable Long id) {
        RestaurantResponse response = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(new BaseResponse<>(1, response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<RestaurantResponse>>> getAll() {
        List<RestaurantResponse> list = restaurantService.getAllRestaurants();
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }

    @GetMapping("/search")
    public ResponseEntity<BaseResponse<List<RestaurantResponse>>> search(@RequestParam String keyword) {
        List<RestaurantResponse> list = restaurantService.findByName(keyword);
        return ResponseEntity.ok(new BaseResponse<>(1, list));
    }
}