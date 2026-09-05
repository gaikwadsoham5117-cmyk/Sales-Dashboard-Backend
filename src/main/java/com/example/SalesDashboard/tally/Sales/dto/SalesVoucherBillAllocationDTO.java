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
public class SalesVoucherBillAllocationDTO {

    private String name;

    private String billType;

    private BigDecimal amount;
}