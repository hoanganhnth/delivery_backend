package com.delivery.livestream_service.dto.request;

import com.delivery.livestream_service.enums.StreamProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateLivestreamRequest {

    @NotBlank(message = "Title không được để trống")
    private String title;

    private String description;

    @NotNull(message = "Restaurant ID không được để trống")
    private Long restaurantId;

    @NotNull(message = "Stream provider không được để trống")
    private StreamProvider streamProvider;
}
