package com.example.SalesDashboard.tally.Sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesVoucherGSTDTO {

    private boolean applicable;

    private BigDecimal cgst;

    private BigDecimal sgst;

    private BigDecimal igst;

    private BigDecimal cess;

    private BigDecimal stateCess;

    public static SalesVoucherGSTDTO empty() {

        return SalesVoucherGSTDTO.builder()
                .applicable(false)
                .cgst(BigDecimal.ZERO)
                .sgst(BigDecimal.ZERO)
                .igst(BigDecimal.ZERO)
                .cess(BigDecimal.ZERO)
                .stateCess(BigDecimal.ZERO)
                .build();
    }
}