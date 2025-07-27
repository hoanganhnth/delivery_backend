package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateRestaurantTransactionRequest;
import com.delivery.restaurant_service.dto.response.RestaurantTransactionResponse;
import com.delivery.restaurant_service.entity.RestaurantTransaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RestaurantTransactionMapper {

    RestaurantTransaction toEntity(CreateRestaurantTransactionRequest request);

    //updateEntityFromDto
//    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
//    RestaurantTransactionResponse updateEntityFromDto(CreateRestaurantBalanceRequest request, RestaurantTransaction transaction);

    RestaurantTransactionResponse toResponse(RestaurantTransaction restaurant);
}
