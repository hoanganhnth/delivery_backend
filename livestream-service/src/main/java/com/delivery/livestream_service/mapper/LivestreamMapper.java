package com.delivery.livestream_service.mapper;

import com.delivery.livestream_service.dto.response.LivestreamProductResponse;
import com.delivery.livestream_service.dto.response.LivestreamResponse;
import com.delivery.livestream_service.entity.Livestream;
import com.delivery.livestream_service.entity.LivestreamProduct;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface LivestreamMapper {

    @Mapping(target = "pinnedProducts", ignore = true)
    LivestreamResponse toResponse(Livestream livestream);

    LivestreamProductResponse toProductResponse(LivestreamProduct product);
}
