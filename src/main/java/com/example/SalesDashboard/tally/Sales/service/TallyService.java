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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TallyService {

    private final ObjectMapper objectMapper;
    private final AgentRelayService agentRelayService;

    // =========================================================
    // CONFIGURATION
    // =========================================================

    private final String tallyCompanyName;
    private final long globalWaitTimeoutSeconds;
    private final long itemParentCacheTtlMinutes;

    // =========================================================
    // DATE-RANGE SAFETY LIMIT
    // =========================================================
    private static final long MAX_DATE_RANGE_DAYS = 31;
    private static final int SUSPICIOUS_RESULT_SIZE = 20_000;

    // =========================================================
    // BOUNDED GLOBAL TALLY CONCURRENCY GATE
    // =========================================================
    private final Semaphore tallyGate;

    // =========================================================
    // ITEM PARENT MAPPING CACHE PER COMPANY
    // =========================================================
    private static class CompanyParentCache {
        private final ConcurrentHashMap<String, String> mappings =
                new ConcurrentHashMap<>();
        private volatile Instant lastUpdated = Instant.EPOCH;

        boolean isFresh(Duration ttl) {
            return Duration.between(lastUpdated, Instant.now()).compareTo(ttl) < 0;
        }
    }

    private final ConcurrentHashMap<String, CompanyParentCache> companyParentCaches =
            new ConcurrentHashMap<>();

    @Autowired
    public TallyService(
            ObjectMapper objectMapper,
            AgentRelayService agentRelayService,
            @Value("${tally.company-name:}") String tallyCompanyName,
            @Value("${tally.max-concurrent-requests:4}") int maxConcurrentRequests,
            @Value("${tally.global-wait-timeout-seconds:120}") long globalWaitTimeoutSeconds,
            @Value("${tally.item-parent-cache-ttl-minutes:60}") long itemParentCacheTtlMinutes
    ) {
        this.objectMapper = objectMapper;
        this.agentRelayService = agentRelayService;
        this.tallyCompanyName = tallyCompanyName;
        this.globalWaitTimeoutSeconds = Math.max(1, globalWaitTimeoutSeconds);
        this.itemParentCacheTtlMinutes = Math.max(1, itemParentCacheTtlMinutes);
        this.tallyGate = new Semaphore(Math.max(1, maxConcurrentRequests), true);
    }

    public TallyService(ObjectMapper objectMapper, AgentRelayService agentRelayService) {
        this(objectMapper, agentRelayService, "", 4, 120, 60);
    }

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

        Map<String, String> headers =
                buildCollectionHeaders("TSPLAllSalesVouchers");

        try (AgentRelayService.RelayResponse response =
                     relayToAgent(
                             userId,
                             headers,
                             request
                     )) {

            try (InputStream stream = response.openStream()) {
                List<SalesVoucherDTO> vouchers =
                        parseSalesVouchersStream(
                                stream,
                                companyName,
                                null,
                                null
                        );

                populateItemParentNames(
                        vouchers,
                        userId,
                        companyName,
                        null,
                        null
                );

                return vouchers;

            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Failed to parse Tally vouchers response: " + e.getMessage()
                );
            }
        }
    }

    // =========================================================
    // DATE-RANGE SALES VOUCHERS
    // =========================================================
    // Uses the existing TSPLAllSalesVouchers collection.
    // SVFromDate and SVToDate are supplied as Tally system variables,
    // and the exact requested range is also enforced by the TDL formula.
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

        Map<String, String> headers =
                buildCollectionHeaders("TSPLAllSalesVouchers");

        try (AgentRelayService.RelayResponse response =
                     relayToAgent(
                             userId,
                             headers,
                             request
                     )) {

            try (InputStream stream = response.openStream()) {
                List<SalesVoucherDTO> vouchers =
                        parseSalesVouchersStream(
                                stream,
                                companyName,
                                from,
                                to
                        );

                populateItemParentNames(
                        vouchers,
                        userId,
                        companyName,
                        from,
                        to
                );

                return vouchers;

            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Failed to parse Tally date-range vouchers response: " + e.getMessage()
                );
            }
        }
    }

    private List<SalesVoucherDTO> parseSalesVouchersStream(
            InputStream inputStream,
            String companyName,
            LocalDate from,
            LocalDate to
    ) {
        List<SalesVoucherDTO> vouchers = new ArrayList<>();
        String tallyStatus = null;

        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Tally returned an invalid response"
                );
            }

            while (parser.nextToken() != JsonToken.END_OBJECT && parser.currentToken() != null) {
                String fieldName = parser.currentName();
                parser.nextToken();

                if ("status".equalsIgnoreCase(fieldName)) {
                    tallyStatus = parser.getText();
                } else if ("data".equalsIgnoreCase(fieldName) && parser.currentToken() == JsonToken.START_OBJECT) {
                    while (parser.nextToken() != JsonToken.END_OBJECT && parser.currentToken() != null) {
                        String dataField = parser.currentName();
                        parser.nextToken();

                        if ("collection".equalsIgnoreCase(dataField) && parser.currentToken() == JsonToken.START_ARRAY) {
                            while (parser.nextToken() != JsonToken.END_ARRAY && parser.currentToken() != null) {
                                try {
                                    JsonNode voucherNode = objectMapper.readTree(parser);
                                    if (voucherNode != null && !voucherNode.isNull()) {
                                        SalesVoucherDTO voucher = mapVoucher(voucherNode);
                                        if (voucher != null) {
                                            vouchers.add(voucher);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.warn("FAILED TO MAP TALLY VOUCHER | company={} | error={}",
                                            companyName, e.getMessage());
                                }
                            }
                        } else {
                            parser.skipChildren();
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to parse Tally response: " + e.getMessage()
            );
        }

        if (!"1".equals(tallyStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally reported an error for company '" + companyName + "'"
            );
        }

        if (from != null && to != null) {
            log.info(
                    "TALLY DATE RANGE RESPONSE | company={} | from={} | to={} | vouchersFromTally={}",
                    companyName,
                    from,
                    to,
                    vouchers.size()
            );
        }

        if (vouchers.size() > SUSPICIOUS_RESULT_SIZE) {
            log.warn(
                    "Tally returned {} vouchers for company {}. Verify the Tally-side date filter is being applied.",
                    vouchers.size(),
                    companyName
            );
        }

        return vouchers;
    }

    // =========================================================
    // BUILD DATE-RANGE TALLY REQUEST
    // =========================================================
    // IMPORTANT:
    // Reuses the existing TSPLAllSalesVouchers collection.
    // Only SVFromDate and SVToDate are changed.
    // =========================================================

    private static final DateTimeFormatter TALLY_DATE_LITERAL_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd-MM-yyyy",
                    java.util.Locale.ENGLISH
            );

    // Tally system date variables use the universal date format YYYYMMDD.
    private static final DateTimeFormatter TALLY_PERIOD_DATE_FORMAT =
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

        String fromPeriod =
                from.format(TALLY_PERIOD_DATE_FORMAT);

        String toPeriod =
                to.format(TALLY_PERIOD_DATE_FORMAT);

        log.info(
                "TALLY DATE RANGE REQUEST | company={} | from={} | to={} | SVFROMDATE={} | SVTODATE={}",
                companyName,
                from,
                to,
                fromPeriod,
                toPeriod
        );

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
                        new TallyRequest.StaticVariable(
                                "svFromDate",
                                fromPeriod
                        ),
                        new TallyRequest.StaticVariable(
                                "svToDate",
                                toPeriod
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
                                "Date, VoucherTypeName, VoucherNumber, PartyLedgerName, Reference, Amount, GUID, MasterID"
                        ),
                        Map.of(
                                "Compute",
                                "PartyParentName : $Parent:Ledger:$PartyLedgerName"
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
        // the Tally formula as $$Date literals.
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

        // Use one range expression instead of generating one equality
        // condition per day. This keeps the TDL formula small and
        // avoids relying on a long OR expression for multi-day ranges.
        String formulaValue =
                "$Date BETWEEN ($$Date:\""
                        + fromDateLiteral
                        + "\") AND ($$Date:\""
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
    // ITEM PARENT ENRICHMENT
    // =========================================================
    //
    // The earlier approach tried to export every Stock Item master
    // and then match the names in Java. That is not the safest way
    // to resolve $Parent:StockItem:$StockItemName for inventory
    // entries.
    //
    // Instead, Tally itself walks the Inventory Entries of the sales
    // vouchers and computes ItemParentName in the Inventory Entry
    // context. This is the same context in which
    // $Parent:StockItem:$StockItemName is valid for each item.
    //
    // Java receives one compact map:
    //     StockItemName -> ItemParentName
    // and enriches every item in the already-fetched vouchers.
    // =========================================================

    private void populateItemParentNames(
            List<SalesVoucherDTO> vouchers,
            String userId,
            String companyName,
            LocalDate from,
            LocalDate to
    ) {

        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }

        // Collect all items that actually need parent lookup
        Set<String> neededItemKeys = new java.util.HashSet<>();
        for (SalesVoucherDTO voucher : vouchers) {
            if (voucher == null || voucher.getItems() == null) continue;
            for (SalesVoucherItemDTO item : voucher.getItems()) {
                if (item == null) continue;
                String existingParent = cleanTallyText(item.getItemParentName());
                String stockItemName = cleanTallyText(item.getStockItemName());
                if ((existingParent == null || existingParent.isBlank())
                        && stockItemName != null && !stockItemName.isBlank()) {
                    neededItemKeys.add(normalizeMasterKey(stockItemName));
                }
            }
        }

        if (neededItemKeys.isEmpty()) {
            return; // 0 auxiliary calls needed!
        }

        String compKey = companyName != null ? companyName.trim() : "default";
        Duration ttl = Duration.ofMinutes(itemParentCacheTtlMinutes);
        CompanyParentCache cache = companyParentCaches.computeIfAbsent(
                compKey,
                k -> new CompanyParentCache()
        );

        // Check if all needed keys are in cache and cache is fresh
        boolean allCached = cache.isFresh(ttl);
        if (allCached) {
            for (String key : neededItemKeys) {
                if (!cache.mappings.containsKey(key)) {
                    allCached = false;
                    break;
                }
            }
        }

        if (!allCached) {
            // Need to fetch from Tally
            Map<String, String> fetched = fetchStockItemParentMap(userId, companyName, from, to);
            if (!fetched.isEmpty()) {
                cache.mappings.putAll(fetched);
                cache.lastUpdated = Instant.now();
            }
        }

        int updatedCount = 0;
        for (SalesVoucherDTO voucher : vouchers) {
            if (voucher == null || voucher.getItems() == null) continue;
            for (SalesVoucherItemDTO item : voucher.getItems()) {
                if (item == null) continue;
                String existingParent = cleanTallyText(item.getItemParentName());
                if (existingParent != null && !existingParent.isBlank()) continue;
                String stockItemName = cleanTallyText(item.getStockItemName());
                if (stockItemName == null || stockItemName.isBlank()) continue;

                String parent = cache.mappings.get(normalizeMasterKey(stockItemName));
                if (parent != null && !parent.isBlank()) {
                    item.setItemParentName(parent);
                    updatedCount++;
                }
            }
        }

        log.info(
                "Resolved item parent names for {} inventory items in company {} (allCached: {})",
                updatedCount,
                companyName,
                allCached
        );
    }

    // =========================================================
    // FETCH ITEM PARENT MAP FROM INVENTORY ENTRY CONTEXT
    // =========================================================

    private Map<String, String> fetchStockItemParentMap(
            String userId,
            String companyName,
            LocalDate from,
            LocalDate to
    ) {

        TallyRequest request =
                buildStockItemParentsRequest(
                        companyName,
                        from,
                        to
                );

        Map<String, String> headers =
                buildCollectionHeaders("TSPLStockItemParents");

        try (AgentRelayService.RelayResponse response =
                     relayToAgent(userId, headers, request)) {

            try (InputStream stream = response.openStream()) {
                return parseStockItemParentsStream(stream, companyName);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Failed to parse Stock Item parents response: " + e.getMessage()
                );
            }
        }
    }

    private Map<String, String> parseStockItemParentsStream(
            InputStream inputStream,
            String companyName
    ) {
        Map<String, String> result = new java.util.HashMap<>();
        String tallyStatus = null;

        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Tally returned an invalid Stock Item parent response"
                );
            }

            while (parser.nextToken() != JsonToken.END_OBJECT && parser.currentToken() != null) {
                String fieldName = parser.currentName();
                parser.nextToken();

                if ("status".equalsIgnoreCase(fieldName)) {
                    tallyStatus = parser.getText();
                } else if ("data".equalsIgnoreCase(fieldName) && parser.currentToken() == JsonToken.START_OBJECT) {
                    while (parser.nextToken() != JsonToken.END_OBJECT && parser.currentToken() != null) {
                        String dataField = parser.currentName();
                        parser.nextToken();

                        if ("collection".equalsIgnoreCase(dataField) && parser.currentToken() == JsonToken.START_ARRAY) {
                            while (parser.nextToken() != JsonToken.END_ARRAY && parser.currentToken() != null) {
                                try {
                                    JsonNode itemNode = objectMapper.readTree(parser);
                                    if (itemNode != null && !itemNode.isNull()) {
                                        String name = cleanTallyText(
                                                extractFirstValue(itemNode, "stockitemname", "name", "itemname")
                                        );
                                        String parent = cleanTallyText(
                                                extractFirstValue(itemNode, "itemparentname", "stockgroup", "stockgroupname", "parent")
                                        );
                                        if (name != null && !name.isBlank() && parent != null && !parent.isBlank()) {
                                            result.put(normalizeMasterKey(name), parent);
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        } else {
                            parser.skipChildren();
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to parse Stock Item parents response: " + e.getMessage()
            );
        }

        if (!"1".equals(tallyStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally reported an error while fetching Stock Item parents for company '" + companyName + "'"
            );
        }

        log.info("Tally returned {} item-parent mappings for company {}", result.size(), companyName);
        return result;
    }

    // =========================================================
    // BUILD ITEM PARENT REQUEST
    // =========================================================

    private TallyRequest buildStockItemParentsRequest(
            String companyName,
            LocalDate from,
            LocalDate to
    ) {

        List<TallyRequest.StaticVariable> staticVariables;

        if (from != null && to != null) {

            String fromPeriod =
                    from.format(TALLY_PERIOD_DATE_FORMAT);

            String toPeriod =
                    to.format(TALLY_PERIOD_DATE_FORMAT);

            staticVariables =
                    List.of(
                            new TallyRequest.StaticVariable(
                                    "svExportFormat",
                                    "jsonex"
                            ),
                            new TallyRequest.StaticVariable(
                                    "svCurrentCompany",
                                    companyName
                            ),
                            new TallyRequest.StaticVariable(
                                    "svFromDate",
                                    fromPeriod
                            ),
                            new TallyRequest.StaticVariable(
                                    "svToDate",
                                    toPeriod
                            )
                    );

        } else {

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
        }

        // ---------------------------------------------------------
        // SOURCE: SALES VOUCHERS
        // ---------------------------------------------------------

        List<Map<String, String>> sourceAttributes =
                new ArrayList<>();

        sourceAttributes.add(
                Map.of(
                        "Type",
                        "Vouchers : VoucherType"
                )
        );

        sourceAttributes.add(
                Map.of(
                        "Child of",
                        "$$VchTypeSales"
                )
        );

        sourceAttributes.add(
                Map.of(
                        "Belongs To",
                        "Yes"
                )
        );

        if (from != null && to != null) {
            sourceAttributes.add(
                    Map.of(
                            "Filter",
                            "TSPLSalesDateRangeFilter"
                    )
            );
        }

        TallyRequest.Definition sourceDefinition =
                TallyRequest.Definition.builder()
                        .metadata(
                                Map.of(
                                        "name",
                                        "TSPLItemParentSource",
                                        "type",
                                        "Collection"
                                )
                        )
                        .attributes(sourceAttributes)
                        .build();

        // ---------------------------------------------------------
        // SUMMARY: WALK INVENTORY ENTRIES
        // ---------------------------------------------------------
        // The critical difference is that Compute is now executed
        // while the current object is an Inventory Entry. Therefore
        // $StockItemName refers to EACH individual item.
        // ---------------------------------------------------------

        List<Map<String, String>> summaryAttributes =
                List.of(
                        Map.of(
                                "Source Collection",
                                "TSPLItemParentSource"
                        ),
                        Map.of(
                                "Walk",
                                "Inventory Entries"
                        ),
                        Map.of(
                                "By",
                                "StockItemName : $StockItemName"
                        ),
                        Map.of(
                                "Compute",
                                "ItemParentName : $Parent:StockItem:$StockItemName"
                        ),
                        Map.of(
                                "Keep Source",
                                "()"
                        )
                );

        TallyRequest.Definition summaryDefinition =
                TallyRequest.Definition.builder()
                        .metadata(
                                Map.of(
                                        "name",
                                        "TSPLStockItemParents",
                                        "type",
                                        "Collection"
                                )
                        )
                        .attributes(summaryAttributes)
                        .build();

        List<TallyRequest.Definition> definitions =
                new ArrayList<>();

        definitions.add(sourceDefinition);
        definitions.add(summaryDefinition);

        // ---------------------------------------------------------
        // SAME DATE FORMULA AS THE SALES RANGE REQUEST
        // ---------------------------------------------------------

        if (from != null && to != null) {

            String fromDateLiteral =
                    from.format(TALLY_DATE_LITERAL_FORMAT);

            String toDateLiteral =
                    to.format(TALLY_DATE_LITERAL_FORMAT);

            String formulaValue =
                    "$Date BETWEEN ($$Date:\""
                            + fromDateLiteral
                            + "\") AND ($$Date:\""
                            + toDateLiteral
                            + "\")";

            TallyRequest.Definition formulaDefinition =
                    TallyRequest.Definition.builder()
                            .metadata(
                                    Map.of(
                                            "name",
                                            "TSPLSalesDateRangeFilter",
                                            "type",
                                            "System",
                                            "sys_type",
                                            "Formulae",
                                            "ismodify",
                                            "true"
                                    )
                            )
                            .value(formulaValue)
                            .build();

            definitions.add(formulaDefinition);
        }

        TallyRequest.TdlMessage message =
                TallyRequest.TdlMessage.builder()
                        .definitions(definitions)
                        .build();

        return TallyRequest.builder()
                .staticVariables(staticVariables)
                .tdlmessage(List.of(message))
                .build();
    }

    // =========================================================
    // NORMALIZE MASTER NAME FOR LOOKUP
    // =========================================================

    private String normalizeMasterKey(String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(java.util.Locale.ROOT);
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

        String reference =
                cleanTallyText(
                        extractValue(
                                voucherNode.path("reference")
                        )
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

        String partyParentName =
                extractFirstValue(
                        voucherNode,
                        "partyparentname",
                        "partygrouponame",
                        "partyparent",
                        "partygpname"
                );

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

                .partyParentName(
                        partyParentName
                )

                .reference(
                        reference
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
    // EXTRACT FIRST NON-EMPTY VALUE FROM MULTIPLE FIELD NAMES
    // =========================================================

    private String extractFirstValue(
            JsonNode node,
            String... fieldNames
    ) {

        if (node == null || node.isNull()) {
            return null;
        }

        for (String fieldName : fieldNames) {

            JsonNode valueNode = node.path(fieldName);

            String value = cleanTallyText(
                    extractValue(valueNode)
            );

            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
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

            String itemParentName =
                    extractFirstValue(
                            itemNode,
                            "itemparentname",
                            "stockitemparentname",
                            "stockgroupname",
                            "parent"
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

                            .itemParentName(
                                    itemParentName
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
                                "Date, VoucherTypeName, VoucherNumber, PartyLedgerName, Reference, GUID, MasterID"
                        ),
                        Map.of(
                                "Compute",
                                "PartyParentName : $Parent:Ledger:$PartyLedgerName"
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

    private Map<String, String> buildCollectionHeaders(String collectionId) {
        return Map.of(
                "Content-Type", "application/json",
                "version", "1",
                "tallyrequest", "export",
                "type", "collection",
                "id", collectionId
        );
    }

    private ResponseEntity<String> callTally(
            String userId,
            TallyRequest requestBody
    ) {

        return callTallyCollection(
                userId,
                requestBody,
                "TSPLAllSalesVouchers"
        );
    }

    // =========================================================
    // GENERIC TALLY COLLECTION CALL
    // =========================================================

    private ResponseEntity<String> callTallyCollection(
            String userId,
            TallyRequest requestBody,
            String collectionId
    ) {

        Map<String, String> headers =
                buildCollectionHeaders(collectionId);

        try (AgentRelayService.RelayResponse response =
                     relayToAgent(
                             userId,
                             headers,
                             requestBody
                     )) {

            return ResponseEntity
                    .status(response.status())
                    .body(response.body());
        }
    }

    // =========================================================
    // COMMON AGENT RELAY
    // =========================================================

    private AgentRelayService.RelayResponse relayToAgent(
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

        boolean acquired;

        try {

            acquired =
                    tallyGate.tryAcquire(
                            globalWaitTimeoutSeconds,
                            TimeUnit.SECONDS
                    );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Interrupted while waiting for Tally to become available"
            );
        }

        if (!acquired) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Tally is busy processing other requests. Please retry shortly."
            );
        }

        AgentRelayService.RelayResponse response = null;

        try {

            response =
                    agentRelayService.relay(
                            userId,
                            "POST",
                            headers,
                            jsonBody
                    );

            if (response == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Tally Agent returned no response"
                );
            }

            int status =
                    response.status();

            if (status < 200
                    || status >= 300) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Tally Agent returned an unsuccessful response: " + status
                );
            }

            response.addCleanupAction(tallyGate::release);
            return response;

        } catch (ResponseStatusException e) {

            if (response != null) {
                response.close();
            } else {
                tallyGate.release();
            }
            throw e;

        } catch (Exception e) {

            if (response != null) {
                response.close();
            } else {
                tallyGate.release();
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to communicate with Tally Agent: " + e.getMessage()
            );
        }
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