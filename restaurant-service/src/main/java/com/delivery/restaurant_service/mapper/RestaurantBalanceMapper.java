package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateRestaurantBalanceRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantBalanceRequest;
import com.delivery.restaurant_service.dto.response.RestaurantBalanceResponse;
import com.delivery.restaurant_service.entity.RestaurantBalance;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface RestaurantBalanceMapper {
    
    @Mapping(target = "restaurantId", source = "restaurant.id")
    RestaurantBalanceResponse toResponse(RestaurantBalance restaurantBalance);

    // update entity from request
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "restaurant", ignore = true)
    void updateEntityFromDto(UpdateRestaurantBalanceRequest request, @MappingTarget RestaurantBalance restaurantBalance);
}
