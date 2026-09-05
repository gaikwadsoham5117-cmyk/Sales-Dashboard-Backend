package com.example.SalesDashboard.tally.Sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesVoucherItemDTO {

    private String stockItemName;

    private BigDecimal rate;

    private String rateUnit;

    private BigDecimal amount;

    private BigDecimal quantity;

    private String quantityUnit;

    /*
     * GST rate details configured for this item
     *
     * Example:
     * CGST      1.50
     * SGST      1.50
     * IGST      3.00
     */
    @Builder.Default
    private List<SalesVoucherGSTRateDTO> gstRates =
            List.of();

    /*
     * Item/category/cost-centre allocations
     */
    @Builder.Default
    private List<SalesVoucherCategoryAllocationDTO> allocations =
            List.of();
}