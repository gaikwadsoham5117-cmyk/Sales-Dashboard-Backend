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
public class SalesVoucherDTO {

    private String date;

    private String voucherTypeName;

    private String voucherNumber;

    private String partyLedgerName;

    private String guid;

    private String masterId;

    private BigDecimal totalAmount;

    /*
     * Inventory items
     */
    @Builder.Default
    private List<SalesVoucherItemDTO> items = List.of();

    /*
     * Accounting ledger entries
     */
    @Builder.Default
    private List<SalesVoucherLedgerDTO> ledgerEntries = List.of();

    /*
     * GST summary for complete voucher
     */
    @Builder.Default
    private SalesVoucherGSTDTO gstDetails =
            SalesVoucherGSTDTO.empty();
}