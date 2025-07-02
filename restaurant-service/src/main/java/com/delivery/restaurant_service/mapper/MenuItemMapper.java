package com.delivery.restaurant_service.mapper;

import com.delivery.restaurant_service.dto.request.CreateMenuItemRequest;
import com.delivery.restaurant_service.dto.request.UpdateMenuItemRequest;
import com.delivery.restaurant_service.dto.response.MenuItemResponse;
import com.delivery.restaurant_service.entity.MenuItem;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface MenuItemMapper {

    MenuItem toEntity(CreateMenuItemRequest request);

    void updateEntityFromDto(UpdateMenuItemRequest request, @MappingTarget MenuItem item);

    MenuItemResponse toResponse(MenuItem item);
}
