package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateRestaurantRequest;
import com.delivery.restaurant_service.dto.request.UpdateRestaurantRequest;
import com.delivery.restaurant_service.dto.response.RestaurantResponse;
import com.delivery.restaurant_service.entity.Restaurant;
import org.mapstruct.*;

import java.time.LocalTime;
@Mapper(componentModel = "spring")
public interface RestaurantMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creatorId", ignore = true)
    @Mapping(target = "rating", ignore = true)
    @Mapping(target = "ratingCount", ignore = true)
    Restaurant toEntity(CreateRestaurantRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "creatorId", ignore = true),
        @Mapping(target = "rating", ignore = true),
        @Mapping(target = "ratingCount", ignore = true)
    })
    void updateEntityFromDto(UpdateRestaurantRequest request, @MappingTarget Restaurant restaurant);

    @Mapping(target = "open", expression = "java(isRestaurantOpen(restaurant.getOpeningHour(), restaurant.getClosingHour()))")
    @Mapping(target = "latitude", source = "addressLat")
    @Mapping(target = "longitude", source = "addressLng")
    RestaurantResponse toResponse(Restaurant restaurant);

    default boolean isRestaurantOpen(LocalTime opening, LocalTime closing) {
        if (opening == null || closing == null) {
            return true; // hoặc throw exception nếu muốn bắt buộc phải có giờ mở/đóng
        }
        LocalTime now = LocalTime.now();
        return now.isAfter(opening) && now.isBefore(closing);
    }
}
