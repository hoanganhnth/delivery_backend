package com.delivery.settlement_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectWithdrawalRequest {
    @Size(max = 500)
    private String reason;
}
