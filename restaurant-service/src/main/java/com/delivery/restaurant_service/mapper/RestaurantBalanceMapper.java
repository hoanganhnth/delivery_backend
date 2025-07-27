package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateRestaurantBalanceRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantBalanceRequest;
import com.delivery.restaurant_service.dto.response.RestaurantBalanceResponse;
import com.delivery.restaurant_service.entity.RestaurantBalance;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface RestaurantBalanceMapper {
    // Define mapping methods here if needed
    // For example, you can map CreateRestaurantBalanceRequest to RestaurantBalanceResponse
    // and vice versa, or any other necessary mappings.
    RestaurantBalanceResponse toResponse(CreateRestaurantBalanceRequest request);

    // update entity from response
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(UpdateRestaurantBalanceRequest request, @MappingTarget RestaurantBalance restaurantBalance);

    CreateRestaurantBalanceRequest toEntity(RestaurantBalanceResponse response);
}
