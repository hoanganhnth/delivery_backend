package com.delivery.restaurant_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateMenuItemInventoryRequest {

    @PositiveOrZero
    @Min(value = 0)
    private Integer onHandQuantity;

    /** Optional optimistic fencing token; required when updating an existing row. */
    @PositiveOrZero
    private Long expectedRevision;
}
