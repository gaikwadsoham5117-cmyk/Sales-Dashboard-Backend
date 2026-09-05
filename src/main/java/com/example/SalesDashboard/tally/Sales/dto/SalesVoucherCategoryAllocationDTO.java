package com.example.SalesDashboard.tally.Sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesVoucherCategoryAllocationDTO {

    private String category;

    private java.math.BigDecimal amount;

    @Builder.Default
    private List<SalesVoucherCostCentreDTO> costCentreAllocations =
            List.of();
}