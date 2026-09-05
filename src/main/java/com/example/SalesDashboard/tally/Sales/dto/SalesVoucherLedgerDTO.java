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
public class SalesVoucherLedgerDTO {

    private String ledgerName;

    private BigDecimal amount;

    private boolean partyLedger;

    private boolean gstLedger;

    @Builder.Default
    private List<SalesVoucherBillAllocationDTO> billAllocations = List.of();

    @Builder.Default
    private List<SalesVoucherCategoryAllocationDTO> categoryAllocations = List.of();
}