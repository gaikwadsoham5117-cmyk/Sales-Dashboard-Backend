package com.example.SalesDashboard.tally.Sales.service;

import com.example.SalesDashboard.agent.service.AgentRelayService;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherBillAllocationDTO;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherCategoryAllocationDTO;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherCostCentreDTO;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherDTO;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherGSTDTO;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherGSTRateDTO;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherItemDTO;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherLedgerDTO;
import com.example.SalesDashboard.tally.Sales.dto.TallyRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TallyService {

    private final ObjectMapper objectMapper;
    private final AgentRelayService agentRelayService;

    // =========================================================
    // CONFIGURATION
    // =========================================================

    @Value("${tally.company-name:}")
    private String tallyCompanyName;

    // =========================================================
    // DATE-RANGE SAFETY LIMIT
    // =========================================================
    // Keep one backend request limited to roughly one month.
    // This protects the Java service from accidentally receiving
    // a very large Tally response. The actual filtering is still
    // performed inside Tally by the date formula below.
    // =========================================================

    private static final long MAX_DATE_RANGE_DAYS = 31;

    private static final int SUSPICIOUS_RESULT_SIZE = 20_000;

    // =========================================================
    // DEFAULT COMPANY
    // =========================================================

    public List<SalesVoucherDTO> pullAllSalesVouchers(
            String userId
    ) {

        return pullAllSalesVouchers(
                userId,
                tallyCompanyName
        );
    }

    // =========================================================
    // EXISTING COMPANY-WISE SALES VOUCHERS
    // =========================================================
    //
    // EXISTING METHOD - KEEPING BEHAVIOUR UNCHANGED
    //
    // =========================================================

    public List<SalesVoucherDTO> pullAllSalesVouchers(
            String userId,
            String companyName
    ) {

        validateUserId(userId);
        validateCompanyName(companyName);

        TallyRequest request =
                buildAllSalesVouchersRequest(companyName);

        ResponseEntity<String> response =
                callTally(
                        userId,
                        request
                );

        String body = response.getBody();

        if (body == null || body.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally returned an empty response"
            );
        }

        JsonNode root;

        try {

            root = objectMapper.readTree(body);

        } catch (Exception e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally returned an invalid response"
            );
        }

        String status =
                root.path("status")
                        .asText("");

        if (!"1".equals(status)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally reported an error for company '"
                            + companyName
                            + "'"
            );
        }

        JsonNode collection =
                root.path("data")
                        .path("collection");

        List<SalesVoucherDTO> vouchers =
                new ArrayList<>();

        if (!collection.isArray()) {
            return vouchers;
        }

        for (JsonNode voucherNode : collection) {

            try {

                SalesVoucherDTO voucher =
                        mapVoucher(voucherNode);

                if (voucher != null) {
                    vouchers.add(voucher);
                }

            } catch (Exception ignored) {

                // One malformed voucher should not
                // fail the complete request.
            }
        }

        return vouchers;
    }

    // =========================================================
    // DATE-RANGE SALES VOUCHERS
    // =========================================================
    // Uses the existing TSPLAllSalesVouchers collection.
    // Only SVFromDate and SVToDate are supplied for the requested range.
    // No new collection and no new date-filter formula are created.
    // =========================================================

    public List<SalesVoucherDTO> pullSalesVouchersByDateRange(
            String userId,
            String companyName,
            LocalDate from,
            LocalDate to
    ) {

        validateUserId(userId);
        validateCompanyName(companyName);

        if (from == null || to == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "From and To dates are required"
            );
        }

        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "From date cannot be after To date"
            );
        }

        long requestedDays =
                ChronoUnit.DAYS.between(from, to);

        if (requestedDays > MAX_DATE_RANGE_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum allowed date range is "
                            + MAX_DATE_RANGE_DAYS
                            + " days"
            );
        }

        TallyRequest request =
                buildSalesVouchersDateRangeRequest(
                        companyName,
                        from,
                        to
                );

        ResponseEntity<String> response =
                callTally(
                        userId,
                        request
                );

        String body = response.getBody();

        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally returned an empty response"
            );
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally returned an invalid response"
            );
        }

        String status =
                root.path("status")
                        .asText("");

        if (!"1".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally reported an error for company '"
                            + companyName
                            + "'"
            );
        }

        JsonNode collection =
                root.path("data")
                        .path("collection");

        List<SalesVoucherDTO> vouchers =
                new ArrayList<>();

        if (!collection.isArray()) {
            return vouchers;
        }

        // This is only a safety canary. We deliberately do NOT
        // filter the response in Java because that would reintroduce
        // the fetch-everything-then-filter pattern.
        if (collection.size() > SUSPICIOUS_RESULT_SIZE) {
            log.warn(
                    "Tally returned {} vouchers for date range {} to {} "
                            + "for company {}. Verify the Tally-side date "
                            + "filter is being applied.",
                    collection.size(),
                    from,
                    to,
                    companyName
            );
        }

        for (JsonNode voucherNode : collection) {
            try {
                SalesVoucherDTO voucher =
                        mapVoucher(voucherNode);

                if (voucher != null) {
                    vouchers.add(voucher);
                }
            } catch (Exception ignored) {
                // One malformed voucher should not fail the complete request.
            }
        }

        return vouchers;
    }

    // =========================================================
    // BUILD DATE-RANGE TALLY REQUEST
    // =========================================================
    // IMPORTANT:
    // Reuses the existing TSPLAllSalesVouchers collection.
    //
    // *** CORRECTION ***
    // SVFROMDATE / SVTODATE are now sent as static variables so
    // the request itself sets Tally's active period for this
    // export, instead of relying on whatever period Tally
    // currently has open (Gateway of Tally > F2: Period).
    //
    // Without these, the Filter formula below can only narrow an
    // ALREADY active period - it cannot widen it - so any range
    // reaching past Tally's current active period silently
    // returned an empty collection. That is why only ranges
    // fully inside the current period (e.g. a single day, or a
    // day already within that period) worked, and any wider
    // range returned [].
    // =========================================================

    private static final DateTimeFormatter TALLY_DATE_LITERAL_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd-MM-yyyy",
                    java.util.Locale.ENGLISH
            );

    private static final DateTimeFormatter TALLY_SV_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyyMMdd",
                    java.util.Locale.ENGLISH
            );

    private TallyRequest buildSalesVouchersDateRangeRequest(
            String companyName,
            LocalDate from,
            LocalDate to
    ) {

        String fromDateLiteral =
                from.format(TALLY_DATE_LITERAL_FORMAT);

        String toDateLiteral =
                to.format(TALLY_DATE_LITERAL_FORMAT);

        String svFromDate =
                from.format(TALLY_SV_DATE_FORMAT);

        String svToDate =
                to.format(TALLY_SV_DATE_FORMAT);

        List<TallyRequest.StaticVariable> staticVariables =
                List.of(
                        new TallyRequest.StaticVariable(
                                "svExportFormat",
                                "jsonex"
                        ),
                        new TallyRequest.StaticVariable(
                                "svCurrentCompany",
                                companyName
                        ),
                        // *** CORRECTION - sets the active period for THIS export ***
                        new TallyRequest.StaticVariable(
                                "svFromDate",
                                svFromDate
                        ),
                        new TallyRequest.StaticVariable(
                                "svToDate",
                                svToDate
                        )
                );

        // ---------------------------------------------------------
        // COLLECTION
        // ---------------------------------------------------------
        // The important part is the Filter attribute (singular). The date
        // range is evaluated by Tally before the JSON collection is
        // returned to Java.
        // ---------------------------------------------------------

        Map<String, Object> collectionMetadata =
                Map.of(
                        "name",
                        "TSPLAllSalesVouchers",
                        "type",
                        "Collection"
                );

        List<Map<String, String>> collectionAttributes =
                List.of(
                        Map.of(
                                "Type",
                                "Vouchers : VoucherType"
                        ),
                        Map.of(
                                "Child of",
                                "$$VchTypeSales"
                        ),
                        Map.of(
                                "Belongs To",
                                "Yes"
                        ),
                        Map.of(
                                "Filter",
                                "TSPLSalesDateRangeFilter"
                        ),
                        Map.of(
                                "Fetch",
                                "Date, VoucherTypeName, VoucherNumber, PartyLedgerName, Amount, GUID, MasterID"
                        ),
                        Map.of(
                                "Fetch",
                                "AllInventoryEntries.List, LedgerEntries.List"
                        )
                );

        TallyRequest.Definition collectionDefinition =
                TallyRequest.Definition.builder()
                        .metadata(collectionMetadata)
                        .attributes(collectionAttributes)
                        .build();

        // ---------------------------------------------------------
        // SYSTEM : FORMULAE
        // ---------------------------------------------------------
        // Do NOT use Parm Var here. Your real Tally test showed that
        // the dynamic HTTP/JSON path can return an empty collection
        // when the Parm Var values do not resolve.
        //
        // Instead the exact requested dates are placed directly into
        // the Tally formula as $$Date literals. This is kept as a
        // second, defensive filter on top of SVFROMDATE / SVTODATE
        // above.
        // ---------------------------------------------------------

        Map<String, Object> formulaMetadata =
                Map.of(
                        "name",
                        "TSPLSalesDateRangeFilter",
                        "type",
                        "System",
                        "sys_type",
                        "Formulae",
                        "ismodify",
                        "true"
                );

        String formulaValue =
                "$Date >= ($$Date:\""
                        + fromDateLiteral
                        + "\") AND $Date <= ($$Date:\""
                        + toDateLiteral
                        + "\")";

        TallyRequest.Definition formulaDefinition =
                TallyRequest.Definition.builder()
                        .metadata(formulaMetadata)
                        .value(formulaValue)
                        .build();

        TallyRequest.TdlMessage message =
                TallyRequest.TdlMessage.builder()
                        .definitions(
                                List.of(
                                        collectionDefinition,
                                        formulaDefinition
                                )
                        )
                        .build();

        return TallyRequest.builder()
                .staticVariables(staticVariables)
                .tdlmessage(
                        List.of(message)
                )
                .build();
    }

    // =========================================================
    // MAP COMPLETE VOUCHER
    // =========================================================

    private SalesVoucherDTO mapVoucher(
            JsonNode voucherNode
    ) {

        if (voucherNode == null
                || voucherNode.isNull()) {

            return null;
        }

        String voucherNumber =
                extractValue(
                        voucherNode.path("vouchernumber")
                );

        // -----------------------------------------------------
        // INVENTORY ITEMS
        // -----------------------------------------------------

        List<SalesVoucherItemDTO> items =
                mapItems(
                        firstArray(
                                voucherNode,
                                "allinventoryentries",
                                "allinventoryentries.list"
                        )
                );

        // -----------------------------------------------------
        // LEDGER ENTRIES
        // -----------------------------------------------------

        List<SalesVoucherLedgerDTO> ledgerEntries =
                mapLedgerEntries(
                        firstArray(
                                voucherNode,
                                "ledgerentries",
                                "ledgerentries.list"
                        )
                );

        // -----------------------------------------------------
        // GST
        // -----------------------------------------------------

        SalesVoucherGSTDTO gstDetails =
                calculateGST(
                        voucherNode,
                        items,
                        ledgerEntries
                );

        // -----------------------------------------------------
        // PARTY LEDGER AMOUNT
        // -----------------------------------------------------

        BigDecimal partyLedgerAmount =
                extractPartyLedgerAmount(
                        voucherNode
                );

        BigDecimal totalAmount;

        if (partyLedgerAmount != null
                && partyLedgerAmount.compareTo(
                BigDecimal.ZERO
        ) != 0) {

            totalAmount =
                    partyLedgerAmount.abs();

        } else {

            totalAmount =
                    calculateTotalAmount(items);
        }

        // -----------------------------------------------------
        // BUILD DTO
        // -----------------------------------------------------

        return SalesVoucherDTO.builder()

                .date(
                        extractValue(
                                voucherNode.path("date")
                        )
                )

                .voucherTypeName(
                        extractValue(
                                voucherNode.path(
                                        "vouchertypename"
                                )
                        )
                )

                .voucherNumber(
                        voucherNumber
                )

                .partyLedgerName(
                        extractValue(
                                voucherNode.path(
                                        "partyledgername"
                                )
                        )
                )

                .guid(
                        extractValue(
                                voucherNode.path("guid")
                        )
                )

                .masterId(
                        extractValue(
                                voucherNode.path("masterid")
                        )
                )

                .totalAmount(
                        totalAmount
                )

                .items(items)

                .ledgerEntries(ledgerEntries)

                .gstDetails(gstDetails)

                .build();
    }

    // =========================================================
    // FIRST ARRAY
    // =========================================================

    private JsonNode firstArray(
            JsonNode parent,
            String... names
    ) {

        if (parent == null) {
            return objectMapper.createArrayNode();
        }

        for (String name : names) {

            JsonNode node =
                    parent.path(name);

            if (node.isArray()) {
                return node;
            }
        }

        return objectMapper.createArrayNode();
    }

    // =========================================================
    // INVENTORY ITEMS
    // =========================================================

    private List<SalesVoucherItemDTO> mapItems(
            JsonNode itemsNode
    ) {

        List<SalesVoucherItemDTO> items =
                new ArrayList<>();

        if (itemsNode == null
                || !itemsNode.isArray()) {

            return items;
        }

        for (JsonNode itemNode : itemsNode) {

            if (itemNode == null
                    || itemNode.isNull()) {

                continue;
            }

            String stockItemName =
                    cleanTallyText(
                            extractValue(
                                    itemNode.path(
                                            "stockitemname"
                                    )
                            )
                    );

            String rate =
                    extractValue(
                            itemNode.path("rate")
                    );

            String amount =
                    extractValue(
                            itemNode.path("amount")
                    );

            String quantity =
                    extractValue(
                            itemNode.path("actualqty")
                    );

            RateParts rateParts =
                    splitRate(rate);

            QuantityParts quantityParts =
                    splitQuantity(quantity);

            List<SalesVoucherGSTRateDTO> gstRates =
                    mapGSTRates(
                            firstArray(
                                    itemNode,
                                    "ratedetails",
                                    "gst.ratedetails",
                                    "gstratedetails"
                            )
                    );

            List<SalesVoucherCategoryAllocationDTO>
                    allocations =
                    mapAccountingAllocations(
                            firstArray(
                                    itemNode,
                                    "accountingallocations",
                                    "accountingallocations.list"
                            )
                    );

            SalesVoucherItemDTO item =
                    SalesVoucherItemDTO.builder()

                            .stockItemName(
                                    stockItemName
                            )

                            .rate(
                                    toBigDecimal(
                                            rateParts.value
                                    )
                            )

                            .rateUnit(
                                    rateParts.unit
                            )

                            .amount(
                                    toBigDecimal(
                                            amount
                                    )
                            )

                            .quantity(
                                    toBigDecimal(
                                            quantityParts.value
                                    )
                            )

                            .quantityUnit(
                                    quantityParts.unit
                            )

                            .gstRates(gstRates)

                            .allocations(allocations)

                            .build();

            items.add(item);
        }

        return items;
    }

    // =========================================================
    // GST RATE DETAILS
    // =========================================================

    private List<SalesVoucherGSTRateDTO> mapGSTRates(
            JsonNode rateDetailsNode
    ) {

        List<SalesVoucherGSTRateDTO> result =
                new ArrayList<>();

        if (rateDetailsNode == null
                || !rateDetailsNode.isArray()) {

            return result;
        }

        for (JsonNode rateNode : rateDetailsNode) {

            if (rateNode == null
                    || rateNode.isNull()) {

                continue;
            }

            String dutyHead =
                    cleanTallyText(
                            extractValue(
                                    rateNode.path(
                                            "gstratedutyhead"
                                    )
                            )
                    );

            String evaluationType =
                    cleanTallyText(
                            extractValue(
                                    rateNode.path(
                                            "gstrateevaluationtype"
                                    )
                            )
                    );

            String rate =
                    extractValue(
                            rateNode.path("gstrate")
                    );

            result.add(
                    SalesVoucherGSTRateDTO.builder()
                            .dutyHead(dutyHead)
                            .evaluationType(
                                    evaluationType
                            )
                            .rate(
                                    toBigDecimal(rate)
                            )
                            .build()
            );
        }

        return result;
    }

    // =========================================================
    // ACCOUNTING ALLOCATIONS
    // =========================================================

    private List<SalesVoucherCategoryAllocationDTO>
    mapAccountingAllocations(
            JsonNode accountingAllocationNode
    ) {

        List<SalesVoucherCategoryAllocationDTO>
                result =
                new ArrayList<>();

        if (accountingAllocationNode == null
                || !accountingAllocationNode.isArray()) {

            return result;
        }

        for (JsonNode accountingAllocation :
                accountingAllocationNode) {

            if (accountingAllocation == null
                    || accountingAllocation.isNull()) {

                continue;
            }

            JsonNode categoryNode =
                    firstArray(
                            accountingAllocation,
                            "categoryallocations",
                            "categoryallocations.list"
                    );

            List<SalesVoucherCategoryAllocationDTO>
                    categoryAllocations =
                    mapCategoryAllocations(
                            categoryNode
                    );

            result.addAll(
                    categoryAllocations
            );
        }

        return result;
    }

    // =========================================================
    // CATEGORY ALLOCATIONS
    // =========================================================

    private List<SalesVoucherCategoryAllocationDTO>
    mapCategoryAllocations(
            JsonNode allocationNode
    ) {

        List<SalesVoucherCategoryAllocationDTO>
                result =
                new ArrayList<>();

        if (allocationNode == null
                || !allocationNode.isArray()) {

            return result;
        }

        for (JsonNode allocation :
                allocationNode) {

            if (allocation == null
                    || allocation.isNull()) {

                continue;
            }

            String category =
                    cleanTallyText(
                            extractValue(
                                    allocation.path(
                                            "category"
                                    )
                            )
                    );

            BigDecimal amount =
                    toBigDecimal(
                            extractValue(
                                    allocation.path(
                                            "amount"
                                    )
                            )
                    );

            List<SalesVoucherCostCentreDTO>
                    costCentreAllocations =
                    mapCostCentreAllocations(
                            firstArray(
                                    allocation,
                                    "costcentreallocations",
                                    "costcentreallocations.list"
                            )
                    );

            result.add(
                    SalesVoucherCategoryAllocationDTO
                            .builder()
                            .category(category)
                            .amount(amount)
                            .costCentreAllocations(
                                    costCentreAllocations
                            )
                            .build()
            );
        }

        return result;
    }

    // =========================================================
    // COST CENTRE ALLOCATIONS
    // =========================================================

    private List<SalesVoucherCostCentreDTO>
    mapCostCentreAllocations(
            JsonNode costCentreNode
    ) {

        List<SalesVoucherCostCentreDTO>
                result =
                new ArrayList<>();

        if (costCentreNode == null
                || !costCentreNode.isArray()) {

            return result;
        }

        for (JsonNode node :
                costCentreNode) {

            if (node == null
                    || node.isNull()) {

                continue;
            }

            String name =
                    cleanTallyText(
                            extractValue(
                                    node.path("name")
                            )
                    );

            BigDecimal amount =
                    toBigDecimal(
                            extractValue(
                                    node.path("amount")
                            )
                    );

            result.add(
                    SalesVoucherCostCentreDTO
                            .builder()
                            .name(name)
                            .amount(amount)
                            .build()
            );
        }

        return result;
    }

    // =========================================================
    // LEDGER ENTRIES
    // =========================================================

    private List<SalesVoucherLedgerDTO>
    mapLedgerEntries(
            JsonNode ledgerNode
    ) {

        List<SalesVoucherLedgerDTO>
                result =
                new ArrayList<>();

        if (ledgerNode == null
                || !ledgerNode.isArray()) {

            return result;
        }

        for (JsonNode ledger :
                ledgerNode) {

            if (ledger == null
                    || ledger.isNull()) {

                continue;
            }

            String ledgerName =
                    cleanTallyText(
                            extractValue(
                                    ledger.path(
                                            "ledgername"
                                    )
                            )
                    );

            BigDecimal amount =
                    toBigDecimal(
                            extractValue(
                                    ledger.path(
                                            "amount"
                                    )
                            )
                    );

            boolean partyLedger =
                    ledger.path(
                            "ispartyledger"
                    ).asBoolean(false);

            boolean gstLedger =
                    isGSTLedger(ledgerName);

            List<SalesVoucherBillAllocationDTO>
                    billAllocations =
                    mapBillAllocations(
                            firstArray(
                                    ledger,
                                    "billallocations",
                                    "billallocations.list"
                            )
                    );

            List<SalesVoucherCategoryAllocationDTO>
                    categoryAllocations =
                    mapCategoryAllocations(
                            firstArray(
                                    ledger,
                                    "categoryallocations",
                                    "categoryallocations.list"
                            )
                    );

            result.add(
                    SalesVoucherLedgerDTO.builder()
                            .ledgerName(ledgerName)
                            .amount(amount)
                            .partyLedger(partyLedger)
                            .gstLedger(gstLedger)
                            .billAllocations(
                                    billAllocations
                            )
                            .categoryAllocations(
                                    categoryAllocations
                            )
                            .build()
            );
        }

        return result;
    }

    // =========================================================
    // BILL ALLOCATIONS
    // =========================================================

    private List<SalesVoucherBillAllocationDTO>
    mapBillAllocations(
            JsonNode billNode
    ) {

        List<SalesVoucherBillAllocationDTO>
                result =
                new ArrayList<>();

        if (billNode == null
                || !billNode.isArray()) {

            return result;
        }

        for (JsonNode bill :
                billNode) {

            if (bill == null
                    || bill.isNull()) {

                continue;
            }

            String name =
                    cleanTallyText(
                            extractValue(
                                    bill.path("name")
                            )
                    );

            String billType =
                    cleanTallyText(
                            extractValue(
                                    bill.path("billtype")
                            )
                    );

            BigDecimal amount =
                    toBigDecimal(
                            extractValue(
                                    bill.path("amount")
                            )
                    );

            result.add(
                    SalesVoucherBillAllocationDTO
                            .builder()
                            .name(name)
                            .billType(billType)
                            .amount(amount)
                            .build()
            );
        }

        return result;
    }

    // =========================================================
    // GST SUMMARY
    // =========================================================

    private SalesVoucherGSTDTO calculateGST(
            JsonNode voucherNode,
            List<SalesVoucherItemDTO> items,
            List<SalesVoucherLedgerDTO> ledgerEntries
    ) {

        BigDecimal cgst =
                BigDecimal.ZERO;

        BigDecimal sgst =
                BigDecimal.ZERO;

        BigDecimal igst =
                BigDecimal.ZERO;

        BigDecimal cess =
                BigDecimal.ZERO;

        BigDecimal stateCess =
                BigDecimal.ZERO;

        boolean applicable =
                voucherNode.path(
                        "vchgststatusisapplicable"
                ).asBoolean(false);

        if (ledgerEntries != null) {

            for (SalesVoucherLedgerDTO ledger :
                    ledgerEntries) {

                if (ledger == null) {
                    continue;
                }

                String ledgerName =
                        ledger.getLedgerName();

                BigDecimal amount =
                        ledger.getAmount();

                if (ledgerName == null
                        || amount == null) {

                    continue;
                }

                String upper =
                        ledgerName
                                .trim()
                                .toUpperCase();

                if (upper.contains("CGST")) {

                    cgst =
                            cgst.add(
                                    amount.abs()
                            );

                    applicable = true;

                } else if (
                        upper.contains("SGST")
                                || upper.contains("UTGST")
                ) {

                    sgst =
                            sgst.add(
                                    amount.abs()
                            );

                    applicable = true;

                } else if (
                        upper.contains("IGST")
                ) {

                    igst =
                            igst.add(
                                    amount.abs()
                            );

                    applicable = true;

                } else if (
                        upper.contains("STATE CESS")
                ) {

                    stateCess =
                            stateCess.add(
                                    amount.abs()
                            );

                    applicable = true;

                } else if (
                        upper.equals("CESS")
                                || (
                                upper.contains("CESS")
                                        && !upper.contains(
                                        "STATE CESS"
                                )
                        )
                ) {

                    cess =
                            cess.add(
                                    amount.abs()
                            );

                    applicable = true;
                }
            }
        }

        if (items != null) {

            for (SalesVoucherItemDTO item :
                    items) {

                if (item == null
                        || item.getGstRates() == null) {

                    continue;
                }

                if (!item.getGstRates().isEmpty()) {
                    applicable = true;
                }
            }
        }

        return SalesVoucherGSTDTO.builder()
                .applicable(applicable)
                .cgst(cgst)
                .sgst(sgst)
                .igst(igst)
                .cess(cess)
                .stateCess(stateCess)
                .build();
    }

    // =========================================================
    // GST LEDGER CHECK
    // =========================================================

    private boolean isGSTLedger(
            String ledgerName
    ) {

        if (ledgerName == null
                || ledgerName.isBlank()) {

            return false;
        }

        String name =
                ledgerName
                        .trim()
                        .toUpperCase();

        return name.contains("CGST")
                || name.contains("SGST")
                || name.contains("UTGST")
                || name.contains("IGST")
                || name.contains("CESS");
    }

    // =========================================================
    // CALCULATE TOTAL
    // =========================================================

    private BigDecimal calculateTotalAmount(
            List<SalesVoucherItemDTO> items
    ) {

        if (items == null
                || items.isEmpty()) {

            return BigDecimal.ZERO;
        }

        BigDecimal total =
                BigDecimal.ZERO;

        for (SalesVoucherItemDTO item :
                items) {

            if (item == null
                    || item.getAmount() == null) {

                continue;
            }

            total =
                    total.add(
                            item.getAmount()
                    );
        }

        return total;
    }

    // =========================================================
    // PARTY LEDGER AMOUNT
    // =========================================================

    private BigDecimal extractPartyLedgerAmount(
            JsonNode voucherNode
    ) {

        JsonNode ledgerEntries =
                firstArray(
                        voucherNode,
                        "ledgerentries",
                        "ledgerentries.list"
                );

        if (!ledgerEntries.isArray()) {
            return null;
        }

        for (JsonNode ledgerEntry :
                ledgerEntries) {

            boolean isPartyLedger =
                    ledgerEntry
                            .path(
                                    "ispartyledger"
                            )
                            .asBoolean(false);

            if (!isPartyLedger) {
                continue;
            }

            String amount =
                    extractValue(
                            ledgerEntry.path(
                                    "amount"
                            )
                    );

            BigDecimal value =
                    toBigDecimal(amount);

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    // =========================================================
    // EXTRACT TALLY VALUE
    // =========================================================

    private String extractValue(
            JsonNode node
    ) {

        if (node == null
                || node.isMissingNode()
                || node.isNull()) {

            return null;
        }

        if (node.isObject()
                && node.has("value")) {

            JsonNode valueNode =
                    node.get("value");

            if (valueNode == null
                    || valueNode.isNull()
                    || valueNode.isMissingNode()) {

                return null;
            }

            if (valueNode.isNumber()) {
                return valueNode.toString();
            }

            return valueNode.asText(null);
        }

        if (node.isNumber()) {
            return node.toString();
        }

        if (node.isTextual()) {
            return node.textValue();
        }

        return null;
    }

    // =========================================================
    // CLEAN TALLY TEXT
    // =========================================================

    private String cleanTallyText(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim();

        if (cleaned.startsWith("\u0004")) {
            return null;
        }

        if (cleaned.equalsIgnoreCase(
                "Not Applicable"
        )) {

            return null;
        }

        return cleaned;
    }

    // =========================================================
    // STRING TO BIG DECIMAL
    // =========================================================

    private BigDecimal toBigDecimal(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        String cleaned =
                value.trim();

        if (cleaned.startsWith("\u0004")) {
            return null;
        }

        cleaned =
                cleaned.replace(",", "");

        cleaned =
                cleaned.replace("₹", "")
                        .trim();

        if (cleaned.startsWith("Rs.")) {

            cleaned =
                    cleaned.substring(3)
                            .trim();
        }

        if (cleaned.startsWith("Rs")) {

            cleaned =
                    cleaned.substring(2)
                            .trim();
        }

        try {

            return new BigDecimal(cleaned);

        } catch (NumberFormatException ignored) {

            return null;
        }
    }

    // =========================================================
    // SPLIT RATE
    // =========================================================

    private RateParts splitRate(
            String rate
    ) {

        if (rate == null
                || rate.isBlank()) {

            return new RateParts(
                    null,
                    null
            );
        }

        String cleaned =
                rate.trim();

        String[] parts =
                cleaned.split(
                        "/",
                        2
                );

        String value =
                parts.length > 0
                        ? parts[0].trim()
                        : null;

        String unit =
                parts.length > 1
                        ? parts[1].trim()
                        : null;

        return new RateParts(
                value,
                unit
        );
    }

    // =========================================================
    // SPLIT QUANTITY
    // =========================================================

    private QuantityParts splitQuantity(
            String quantity
    ) {

        if (quantity == null
                || quantity.isBlank()) {

            return new QuantityParts(
                    null,
                    null
            );
        }

        String cleaned =
                quantity.trim();

        Pattern pattern =
                Pattern.compile(
                        "^([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$"
                );

        Matcher matcher =
                pattern.matcher(cleaned);

        if (!matcher.matches()) {

            return new QuantityParts(
                    null,
                    null
            );
        }

        String value =
                matcher.group(1);

        String unit =
                matcher.group(2);

        if (unit != null) {
            unit = unit.trim();
        }

        return new QuantityParts(
                value,
                unit
        );
    }

    // =========================================================
    // VALIDATE USER
    // =========================================================

    private void validateUserId(
            String userId
    ) {

        if (userId == null
                || userId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required"
            );
        }
    }

    // =========================================================
    // VALIDATE COMPANY
    // =========================================================

    private void validateCompanyName(
            String companyName
    ) {

        if (companyName == null
                || companyName.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Company name cannot be empty"
            );
        }
    }

    // =========================================================
    // EXISTING BUILD TALLY REQUEST
    // =========================================================
    //
    // EXISTING REQUEST - KEEPING IT SEPARATE
    //
    // =========================================================

    private TallyRequest buildAllSalesVouchersRequest(
            String companyName
    ) {

        List<TallyRequest.StaticVariable>
                staticVariables =
                List.of(

                        new TallyRequest.StaticVariable(
                                "svExportFormat",
                                "jsonex"
                        ),

                        new TallyRequest.StaticVariable(
                                "svCurrentCompany",
                                companyName
                        )
                );

        // -----------------------------------------------------
        // COLLECTION
        // -----------------------------------------------------

        Map<String, Object> metadata =
                Map.of(
                        "name",
                        "TSPLAllSalesVouchers",

                        "type",
                        "Collection"
                );

        List<Map<String, String>> attributes =
                List.of(

                        Map.of(
                                "Type",
                                "Vouchers : VoucherType"
                        ),

                        Map.of(
                                "Child of",
                                "$$VchTypeSales"
                        ),

                        Map.of(
                                "Belongs To",
                                "Yes"
                        ),

                        Map.of(
                                "Fetch",
                                "Date, VoucherTypeName, VoucherNumber, PartyLedgerName, GUID, MasterID"
                        ),

                        Map.of(
                                "Fetch",
                                "AllInventoryEntries.List, LedgerEntries.List"
                        )
                );

        TallyRequest.Definition definition =
                TallyRequest.Definition.builder()
                        .metadata(metadata)
                        .attributes(attributes)
                        .build();

        TallyRequest.TdlMessage message =
                TallyRequest.TdlMessage.builder()
                        .definitions(
                                List.of(definition)
                        )
                        .build();

        return TallyRequest.builder()
                .staticVariables(
                        staticVariables
                )
                .tdlmessage(
                        List.of(message)
                )
                .build();
    }

    // =========================================================
    // EXISTING CALL TALLY
    // =========================================================
    //
    // EXISTING API REQUEST ID
    //
    // TSPLAllSalesVouchers
    //
    // =========================================================

    private ResponseEntity<String> callTally(
            String userId,
            TallyRequest requestBody
    ) {

        Map<String, String> headers =
                Map.of(
                        "Content-Type",
                        "application/json",

                        "version",
                        "1",

                        "tallyrequest",
                        "export",

                        "type",
                        "collection",

                        "id",
                        "TSPLAllSalesVouchers"
                );

        return relayToAgent(
                userId,
                headers,
                requestBody
        );
    }

    // =========================================================
    // COMMON AGENT RELAY
    // =========================================================

    private ResponseEntity<String> relayToAgent(
            String userId,
            Map<String, String> headers,
            TallyRequest requestBody
    ) {

        String jsonBody;

        try {

            jsonBody =
                    objectMapper.writeValueAsString(
                            requestBody
                    );

        } catch (Exception e) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to build Tally request"
            );
        }

        AgentRelayService.RelayResponse response;

        try {

            response =
                    agentRelayService.relay(
                            userId,
                            "POST",
                            headers,
                            jsonBody
                    );

        } catch (ResponseStatusException e) {

            throw e;

        } catch (Exception e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to communicate with Tally Agent"
            );
        }

        if (response == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally Agent returned no response"
            );
        }

        int status =
                response.status();

        String body =
                response.body();

        if (status < 200
                || status >= 300) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally Agent returned an unsuccessful response"
            );
        }

        return ResponseEntity
                .status(status)
                .body(body);
    }

    // =========================================================
    // RATE PARTS
    // =========================================================

    private static class RateParts {

        private final String value;
        private final String unit;

        private RateParts(
                String value,
                String unit
        ) {

            this.value = value;
            this.unit = unit;
        }
    }

    // =========================================================
    // QUANTITY PARTS
    // =========================================================

    private static class QuantityParts {

        private final String value;
        private final String unit;

        private QuantityParts(
                String value,
                String unit
        ) {

            this.value = value;
            this.unit = unit;
        }
    }
}