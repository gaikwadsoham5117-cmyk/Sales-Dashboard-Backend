// // package com.example.SalesDashboard.tally.Sales.service;
// // import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherDTO;
// // import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherItemDTO;
// // import com.example.SalesDashboard.tally.Sales.dto.TallyRequest;
// // import com.fasterxml.jackson.databind.JsonNode;
// // import com.fasterxml.jackson.databind.ObjectMapper;
// // import lombok.RequiredArgsConstructor;
// // import lombok.extern.slf4j.Slf4j;
// // import org.springframework.beans.factory.annotation.Value;
// // import org.springframework.http.HttpEntity;
// // import org.springframework.http.HttpHeaders;
// // import org.springframework.http.HttpMethod;
// // import org.springframework.http.HttpStatus;
// // import org.springframework.http.MediaType;
// // import org.springframework.http.ResponseEntity;
// // import org.springframework.stereotype.Service;
// // import org.springframework.web.client.ResourceAccessException;
// // import org.springframework.web.client.RestClientResponseException;
// // import org.springframework.web.client.RestTemplate;
// // import org.springframework.web.server.ResponseStatusException;
// // import java.math.BigDecimal;
// // import java.util.ArrayList;
// // import java.util.List;
// // import java.util.Map;
// // import java.util.regex.Matcher;
// // import java.util.regex.Pattern;
// // @Slf4j
// // @Service
// // @RequiredArgsConstructor
// // public class TallyService {
// //     private final RestTemplate restTemplate;
// //     private final ObjectMapper objectMapper;
// //     // =========================================================
// //     // TALLY CONFIGURATION
// //     // =========================================================
// //     @Value("${tally.base-url:http://127.0.0.1:9000}")
// //     private String tallyBaseUrl;
// //     @Value("${tally.company-name}")
// //     private String tallyCompanyName;
// //     // =========================================================
// //     // DEFAULT COMPANY - RAW SALES VOUCHERS
// //     // =========================================================
// //     public JsonNode pullAllSalesVouchersRaw() {
// //         return pullAllSalesVouchersRaw(tallyCompanyName);
// //     }
// //     // =========================================================
// //     // COMPANY-WISE - RAW SALES VOUCHERS
// //     // =========================================================
// //     public JsonNode pullAllSalesVouchersRaw(
// //             String companyName
// //     ) {
// //         validateCompanyName(companyName);
// //         log.info(
// //                 "Fetching raw sales vouchers for company: {}",
// //                 companyName
// //         );
// //         ResponseEntity<String> response
// //                 = callTally(
// //                         buildAllSalesVouchersRequest(
// //                                 companyName
// //                         )
// //                 );
// //         try {
// //             return objectMapper.readTree(
// //                     response.getBody()
// //             );
// //         } catch (Exception e) {
// //             log.error(
// //                     "Failed to parse Tally response as JSON for company: {}",
// //                     companyName,
// //                     e
// //             );
// //             throw new ResponseStatusException(
// //                     HttpStatus.BAD_GATEWAY,
// //                     "Tally returned an unparsable response"
// //             );
// //         }
// //     }
// //     // =========================================================
// //     // DEFAULT COMPANY - SALES VOUCHERS
// //     // =========================================================
// //     public List<SalesVoucherDTO> pullAllSalesVouchers() {
// //         return pullAllSalesVouchers(
// //                 tallyCompanyName
// //         );
// //     }
// //     // =========================================================
// //     // COMPANY-WISE - SALES VOUCHERS
// //     // =========================================================
// //     public List<SalesVoucherDTO> pullAllSalesVouchers(
// //             String companyName
// //     ) {
// //         validateCompanyName(companyName);
// //         log.info(
// //                 "Fetching sales vouchers for company: {}",
// //                 companyName
// //         );
// //         JsonNode root
// //                 = pullAllSalesVouchersRaw(
// //                         companyName
// //                 );
// //         String status
// //                 = root.path("status")
// //                         .asText("");
// //         if (!"1".equals(status)) {
// //             log.warn(
// //                     "Tally responded with non-success status for company {}: {}",
// //                     companyName,
// //                     root
// //             );
// //             throw new ResponseStatusException(
// //                     HttpStatus.BAD_GATEWAY,
// //                     "Tally reported an error for company '"
// //                     + companyName
// //                     + "'. Raw response: "
// //                     + root
// //             );
// //         }
// //         JsonNode collection
// //                 = root.path("data")
// //                         .path("collection");
// //         List<SalesVoucherDTO> vouchers
// //                 = new ArrayList<>();
// //         if (!collection.isArray()) {
// //             log.warn(
// //                     "Tally collection is not an array for company: {}",
// //                     companyName
// //             );
// //             return vouchers;
// //         }
// //         for (JsonNode voucherNode : collection) {
// //             vouchers.add(
// //                     mapVoucher(voucherNode)
// //             );
// //         }
// //         log.info(
// //                 "Fetched {} sales vouchers from company: {}",
// //                 vouchers.size(),
// //                 companyName
// //         );
// //         return vouchers;
// //     }
// //     // =========================================================
// //     // MAP VOUCHER
// //     // =========================================================
// //     private SalesVoucherDTO mapVoucher(
// //             JsonNode voucherNode
// //     ) {
// //         String voucherNumber
// //                 = extractValue(
// //                         voucherNode.path(
// //                                 "vouchernumber"
// //                         )
// //                 );
// //         // -----------------------------------------------------
// //         // MAP INVENTORY ITEMS
// //         // -----------------------------------------------------
// //         List<SalesVoucherItemDTO> items
// //                 = mapItems(
// //                         voucherNode.path(
// //                                 "allinventoryentries"
// //                         )
// //                 );
// //         // -----------------------------------------------------
// //         // GET ACTUAL VOUCHER TOTAL
// //         //
// //         // 1. Try party ledger amount
// //         // 2. If unavailable, calculate from item amounts
// //         // -----------------------------------------------------
// //         BigDecimal partyLedgerAmount
// //                 = extractPartyLedgerAmount(
// //                         voucherNode
// //                 );
// //         BigDecimal totalAmount;
// //         if (partyLedgerAmount != null
// //                 && partyLedgerAmount.compareTo(
// //                         BigDecimal.ZERO
// //                 ) != 0) {
// //             totalAmount
// //                     = partyLedgerAmount.abs();
// //             log.debug(
// //                     "Voucher {} total from party ledger = {}",
// //                     voucherNumber,
// //                     totalAmount
// //             );
// //         } else {
// //             totalAmount
// //                     = calculateTotalAmount(
// //                             items
// //                     );
// //             log.debug(
// //                     "Voucher {} total calculated from inventory = {}",
// //                     voucherNumber,
// //                     totalAmount
// //             );
// //         }
// //         // -----------------------------------------------------
// //         // BUILD DTO
// //         // -----------------------------------------------------
// //         return SalesVoucherDTO.builder()
// //                 .date(
// //                         extractValue(
// //                                 voucherNode.path(
// //                                         "date"
// //                                 )
// //                         )
// //                 )
// //                 .voucherTypeName(
// //                         extractValue(
// //                                 voucherNode.path(
// //                                         "vouchertypename"
// //                                 )
// //                         )
// //                 )
// //                 .voucherNumber(
// //                         voucherNumber
// //                 )
// //                 .partyLedgerName(
// //                         extractValue(
// //                                 voucherNode.path(
// //                                         "partyledgername"
// //                                 )
// //                         )
// //                 )
// //                 .guid(
// //                         extractValue(
// //                                 voucherNode.path(
// //                                         "guid"
// //                                 )
// //                         )
// //                 )
// //                 .masterId(
// //                         extractValue(
// //                                 voucherNode.path(
// //                                         "masterid"
// //                                 )
// //                         )
// //                 )
// //                 .totalAmount(
// //                         totalAmount
// //                 )
// //                 .items(
// //                         items
// //                 )
// //                 .build();
// //     }
// //     // =========================================================
// //     // CALCULATE TOTAL FROM INVENTORY ITEMS
// //     // =========================================================
// //     private BigDecimal calculateTotalAmount(
// //             List<SalesVoucherItemDTO> items
// //     ) {
// //         if (items == null
// //                 || items.isEmpty()) {
// //             return BigDecimal.ZERO;
// //         }
// //         BigDecimal total
// //                 = BigDecimal.ZERO;
// //         for (SalesVoucherItemDTO item : items) {
// //             if (item == null) {
// //                 continue;
// //             }
// //             BigDecimal amount
// //                     = item.getAmount();
// //             if (amount == null) {
// //                 continue;
// //             }
// //             total
// //                     = total.add(amount);
// //         }
// //         return total;
// //     }
// //     // =========================================================
// //     // EXTRACT PARTY LEDGER TOTAL
// //     // =========================================================
// //     private BigDecimal extractPartyLedgerAmount(
// //             JsonNode voucherNode
// //     ) {
// //         JsonNode ledgerEntries
// //                 = voucherNode.path(
// //                         "ledgerentries"
// //                 );
// //         if (!ledgerEntries.isArray()) {
// //             log.debug(
// //                     "ledgerentries not available for voucher {}",
// //                     extractValue(
// //                             voucherNode.path(
// //                                     "vouchernumber"
// //                             )
// //                     )
// //             );
// //             return null;
// //         }
// //         for (JsonNode ledgerEntry
// //                 : ledgerEntries) {
// //             boolean isPartyLedger
// //                     = ledgerEntry
// //                             .path("ispartyledger")
// //                             .asBoolean(false);
// //             if (!isPartyLedger) {
// //                 continue;
// //             }
// //             String amount
// //                     = extractValue(
// //                             ledgerEntry.path(
// //                                     "amount"
// //                             )
// //                     );
// //             BigDecimal value
// //                     = toBigDecimal(amount);
// //             if (value != null) {
// //                 log.debug(
// //                         "Party ledger amount for voucher {} = {}",
// //                         extractValue(
// //                                 voucherNode.path(
// //                                         "vouchernumber"
// //                                 )
// //                         ),
// //                         value
// //                 );
// //                 return value;
// //             }
// //         }
// //         return null;
// //     }
// //     // =========================================================
// //     // MAP INVENTORY ITEMS
// //     // =========================================================
// //     private List<SalesVoucherItemDTO> mapItems(
// //             JsonNode itemsNode
// //     ) {
// //         List<SalesVoucherItemDTO> items
// //                 = new ArrayList<>();
// //         if (!itemsNode.isArray()) {
// //             return items;
// //         }
// //         for (JsonNode itemNode
// //                 : itemsNode) {
// //             String stockItemName
// //                     = extractValue(
// //                             itemNode.path(
// //                                     "stockitemname"
// //                             )
// //                     );
// //             String rate
// //                     = extractValue(
// //                             itemNode.path(
// //                                     "rate"
// //                             )
// //                     );
// //             String amount
// //                     = extractValue(
// //                             itemNode.path(
// //                                     "amount"
// //                             )
// //                     );
// //             String quantity
// //                     = extractValue(
// //                             itemNode.path(
// //                                     "actualqty"
// //                             )
// //                     );
// //             RateParts rateParts
// //                     = splitRate(rate);
// //             QuantityParts quantityParts
// //                     = splitQuantity(quantity);
// //             SalesVoucherItemDTO item
// //                     = SalesVoucherItemDTO.builder()
// //                             .stockItemName(
// //                                     stockItemName
// //                             )
// //                             .rate(
// //                                     toBigDecimal(
// //                                             rateParts.value
// //                                     )
// //                             )
// //                             .rateUnit(
// //                                     rateParts.unit
// //                             )
// //                             .amount(
// //                                     toBigDecimal(
// //                                             amount
// //                                     )
// //                             )
// //                             .quantity(
// //                                     toBigDecimal(
// //                                             quantityParts.value
// //                                     )
// //                             )
// //                             .quantityUnit(
// //                                     quantityParts.unit
// //                             )
// //                             .build();
// //             items.add(item);
// //         }
// //         return items;
// //     }
// //     // =========================================================
// //     // SPLIT RATE
// //     // =========================================================
// //     private RateParts splitRate(
// //             String rate
// //     ) {
// //         if (rate == null
// //                 || rate.isBlank()) {
// //             return new RateParts(
// //                     null,
// //                     null
// //             );
// //         }
// //         String cleaned
// //                 = rate.trim();
// //         String[] parts
// //                 = cleaned.split(
// //                         "/",
// //                         2
// //                 );
// //         String value
// //                 = parts.length > 0
// //                         ? parts[0].trim()
// //                         : null;
// //         String unit
// //                 = parts.length > 1
// //                         ? parts[1].trim()
// //                         : null;
// //         return new RateParts(
// //                 value,
// //                 unit
// //         );
// //     }
// //     // =========================================================
// //     // SPLIT QUANTITY
// //     // =========================================================
// //     private QuantityParts splitQuantity(
// //             String quantity
// //     ) {
// //         if (quantity == null
// //                 || quantity.isBlank()) {
// //             return new QuantityParts(
// //                     null,
// //                     null
// //             );
// //         }
// //         String cleaned
// //                 = quantity.trim();
// //         Pattern pattern
// //                 = Pattern.compile(
// //                         "^([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$"
// //                 );
// //         Matcher matcher
// //                 = pattern.matcher(cleaned);
// //         if (!matcher.matches()) {
// //             return new QuantityParts(
// //                     null,
// //                     null
// //             );
// //         }
// //         String value
// //                 = matcher.group(1);
// //         String unit
// //                 = matcher.group(2);
// //         if (unit != null) {
// //             unit
// //                     = unit.trim();
// //         }
// //         return new QuantityParts(
// //                 value,
// //                 unit
// //         );
// //     }
// //     // =========================================================
// //     // EXTRACT TALLY VALUE
// //     // =========================================================
// //     private String extractValue(
// //             JsonNode node
// //     ) {
// //         if (node == null
// //                 || node.isMissingNode()
// //                 || node.isNull()) {
// //             return null;
// //         }
// //         // -----------------------------------------------------
// //         // Tally JSONEX object
// //         //
// //         // {
// //         //     "value": "1000"
// //         // }
// //         // -----------------------------------------------------
// //         if (node.isObject()
// //                 && node.has("value")) {
// //             return node
// //                     .path("value")
// //                     .asText(null);
// //         }
// //         // -----------------------------------------------------
// //         // STRING OR NUMBER
// //         // -----------------------------------------------------
// //         if (node.isTextual()
// //                 || node.isNumber()) {
// //             return node.asText();
// //         }
// //         return null;
// //     }
// //     // =========================================================
// //     // STRING -> BIG DECIMAL
// //     // =========================================================
// //     private BigDecimal toBigDecimal(
// //             String value
// //     ) {
// //         if (value == null
// //                 || value.isBlank()) {
// //             return null;
// //         }
// //         try {
// //             return new BigDecimal(
// //                     value.trim()
// //             );
// //         } catch (NumberFormatException e) {
// //             log.warn(
// //                     "Could not convert '{}' to BigDecimal",
// //                     value
// //             );
// //             return null;
// //         }
// //     }
// //     // =========================================================
// //     // VALIDATE COMPANY NAME
// //     // =========================================================
// //     private void validateCompanyName(
// //             String companyName
// //     ) {
// //         if (companyName == null
// //                 || companyName.isBlank()) {
// //             throw new ResponseStatusException(
// //                     HttpStatus.BAD_REQUEST,
// //                     "Company name cannot be empty"
// //             );
// //         }
// //     }
// //     // =========================================================
// //     // RATE PARTS
// //     // =========================================================
// //     private static class RateParts {
// //         private final String value;
// //         private final String unit;
// //         private RateParts(
// //                 String value,
// //                 String unit
// //         ) {
// //             this.value = value;
// //             this.unit = unit;
// //         }
// //     }
// //     // =========================================================
// //     // QUANTITY PARTS
// //     // =========================================================
// //     private static class QuantityParts {
// //         private final String value;
// //         private final String unit;
// //         private QuantityParts(
// //                 String value,
// //                 String unit
// //         ) {
// //             this.value = value;
// //             this.unit = unit;
// //         }
// //     }
// //     // =========================================================
// //     // BUILD TALLY REQUEST
// //     // COMPANY-WISE
// //     // =========================================================
// //     private TallyRequest buildAllSalesVouchersRequest(
// //             String companyName
// //     ) {
// //         return TallyRequest.builder()
// //                 .staticVariables(
// //                         List.of(
// //                                 new TallyRequest.StaticVariable(
// //                                         "svExportFormat",
// //                                         "jsonex"
// //                                 ),
// //                                 /*
// //                                  * COMPANY IS NOW DYNAMIC
// //                                  *
// //                                  * Default API:
// //                                  *     tallyCompanyName
// //                                  *
// //                                  * Company-wise API:
// //                                  *     companyName
// //                                  */
// //                                 new TallyRequest.StaticVariable(
// //                                         "svCurrentCompany",
// //                                         companyName
// //                                 )
// //                         )
// //                 )
// //                 .tdlmessage(
// //                         List.of(
// //                                 TallyRequest.TdlMessage
// //                                         .builder()
// //                                         .definitions(
// //                                                 List.of(
// //                                                         TallyRequest.Definition
// //                                                                 .builder()
// //                                                                 .metadata(
// //                                                                         Map.of(
// //                                                                                 "name",
// //                                                                                 "TSPL All Sales Vouchers",
// //                                                                                 "type",
// //                                                                                 "Collection"
// //                                                                         )
// //                                                                 )
// //                                                                 .attributes(
// //                                                                         List.of(
// //                                                                                 Map.of(
// //                                                                                         "Type",
// //                                                                                         "Vouchers:VoucherType"
// //                                                                                 ),
// //                                                                                 Map.of(
// //                                                                                         "Child Of",
// //                                                                                         "$$VchTypeSales"
// //                                                                                 ),
// //                                                                                 Map.of(
// //                                                                                         "Native Method",
// //                                                                                         "Date, VoucherTypeName, VoucherNumber, PartyLedgerName, GUID, MasterID, AllInventoryEntries.List, LedgerEntries.List"
// //                                                                                 )
// //                                                                         )
// //                                                                 )
// //                                                                 .build()
// //                                                 )
// //                                         )
// //                                         .build()
// //                         )
// //                 )
// //                 .build();
// //     }
// //     // =========================================================
// //     // CALL TALLY
// //     // =========================================================
// //     private ResponseEntity<String> callTally(
// //             TallyRequest requestBody
// //     ) {
// //         HttpHeaders headers
// //                 = new HttpHeaders();
// //         headers.setContentType(
// //                 MediaType.APPLICATION_JSON
// //         );
// //         headers.set(
// //                 "version",
// //                 "1"
// //         );
// //         headers.set(
// //                 "tallyrequest",
// //                 "export"
// //         );
// //         headers.set(
// //                 "type",
// //                 "collection"
// //         );
// //         headers.set(
// //                 "id",
// //                 "TSPLAllSalesVouchers"
// //         );
// //         // -----------------------------------------------------
// //         // SERIALIZE REQUEST
// //         // -----------------------------------------------------
// //         String jsonBody;
// //         try {
// //             jsonBody
// //                     = objectMapper.writeValueAsString(
// //                             requestBody
// //                     );
// //         } catch (Exception e) {
// //             log.error(
// //                     "Failed to serialize Tally request body",
// //                     e
// //             );
// //             throw new ResponseStatusException(
// //                     HttpStatus.INTERNAL_SERVER_ERROR,
// //                     "Failed to build Tally request"
// //             );
// //         }
// //         log.debug(
// //                 "Sending request to Tally at {}",
// //                 tallyBaseUrl
// //         );
// //         log.debug(
// //                 "Tally request company: {}",
// //                 extractCompanyFromRequest(
// //                         requestBody
// //                 )
// //         );
// //         HttpEntity<String> entity
// //                 = new HttpEntity<>(
// //                         jsonBody,
// //                         headers
// //                 );
// //         // -----------------------------------------------------
// //         // SEND REQUEST TO TALLY
// //         // -----------------------------------------------------
// //         try {
// //             ResponseEntity<String> response
// //                     = restTemplate.exchange(
// //                             tallyBaseUrl,
// //                             HttpMethod.POST,
// //                             entity,
// //                             String.class
// //                     );
// //             log.debug(
// //                     "Tally response status: {}",
// //                     response.getStatusCode()
// //             );
// //             return response;
// //         } catch (ResourceAccessException e) {
// //             log.error(
// //                     "Could not reach TallyPrime at {}. "
// //                     + "Check that TallyPrime and the connector are running.",
// //                     tallyBaseUrl,
// //                     e
// //             );
// //             throw new ResponseStatusException(
// //                     HttpStatus.SERVICE_UNAVAILABLE,
// //                     "Could not reach TallyPrime at "
// //                     + tallyBaseUrl
// //                     + ". Ensure TallyPrime and TallyAPIConnectorV2.0.exe are running."
// //             );
// //         } catch (RestClientResponseException e) {
// //             log.error(
// //                     "Tally returned an error response: {} - {}",
// //                     e.getStatusCode(),
// //                     e.getResponseBodyAsString()
// //             );
// //             throw new ResponseStatusException(
// //                     HttpStatus.BAD_GATEWAY,
// //                     "Tally returned an error: "
// //                     + e.getResponseBodyAsString()
// //             );
// //         }
// //     }
// //     // =========================================================
// //     // EXTRACT COMPANY FOR LOGGING
// //     // =========================================================
// //     private String extractCompanyFromRequest(
// //             TallyRequest requestBody
// //     ) {
// //         try {
// //             JsonNode node
// //                     = objectMapper.valueToTree(
// //                             requestBody
// //                     );
// //             JsonNode staticVariables
// //                     = node.path(
// //                             "staticVariables"
// //                     );
// //             if (staticVariables.isArray()) {
// //                 for (JsonNode variable
// //                         : staticVariables) {
// //                     String name
// //                             = variable.path("name")
// //                                     .asText("");
// //                     if ("svCurrentCompany".equals(
// //                             name
// //                     )) {
// //                         return variable
// //                                 .path("value")
// //                                 .asText("");
// //                     }
// //                 }
// //             }
// //         } catch (Exception e) {
// //             log.debug(
// //                     "Could not extract company from Tally request"
// //             );
// //         }
// //         return "unknown";
// //     }
// // }
// package com.example.SalesDashboard.tally.Sales.service;

// import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherDTO;
// import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherItemDTO;
// import com.example.SalesDashboard.tally.Sales.dto.TallyRequest;

// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;

// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.http.HttpEntity;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.HttpMethod;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.stereotype.Service;
// import org.springframework.web.client.ResourceAccessException;
// import org.springframework.web.client.RestClientResponseException;
// import org.springframework.web.client.RestTemplate;
// import org.springframework.web.server.ResponseStatusException;

// import java.math.BigDecimal;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Map;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// @Slf4j
// @Service
// @RequiredArgsConstructor
// public class TallyService {

//     private final RestTemplate restTemplate;
//     private final ObjectMapper objectMapper;

//     // =========================================================
//     // TALLY CONFIGURATION
//     // =========================================================
//     @Value("${tally.base-url:http://127.0.0.1:9000}")
//     private String tallyBaseUrl;

//     @Value("${tally.company-name}")
//     private String tallyCompanyName;

//     // =========================================================
//     // DEFAULT COMPANY - RAW SALES VOUCHERS
//     // =========================================================
//     public JsonNode pullAllSalesVouchersRaw() {
//         return pullAllSalesVouchersRaw(tallyCompanyName);
//     }

//     // =========================================================
//     // COMPANY-WISE - RAW SALES VOUCHERS
//     // =========================================================
//     public JsonNode pullAllSalesVouchersRaw(String companyName) {

//         validateCompanyName(companyName);

//         log.info(
//                 "Fetching all sales vouchers for company: {}",
//                 companyName
//         );

//         ResponseEntity<String> response
//                 = callTally(
//                         buildAllSalesVouchersRequest(companyName)
//                 );

//         try {

//             return objectMapper.readTree(
//                     response.getBody()
//             );

//         } catch (Exception e) {

//             log.error(
//                     "Failed to parse Tally response as JSON for company: {}",
//                     companyName,
//                     e
//             );

//             throw new ResponseStatusException(
//                     HttpStatus.BAD_GATEWAY,
//                     "Tally returned an unparsable response"
//             );
//         }
//     }

//     // =========================================================
//     // DEFAULT COMPANY - SALES VOUCHERS
//     // =========================================================
//     public List<SalesVoucherDTO> pullAllSalesVouchers() {

//         return pullAllSalesVouchers(
//                 tallyCompanyName
//         );
//     }

//     // =========================================================
//     // COMPANY-WISE - SALES VOUCHERS
//     // =========================================================
//     public List<SalesVoucherDTO> pullAllSalesVouchers(
//             String companyName
//     ) {

//         validateCompanyName(companyName);

//         log.info(
//                 "Fetching all sales vouchers for company: {}",
//                 companyName
//         );

//         JsonNode root
//                 = pullAllSalesVouchersRaw(companyName);

//         String status
//                 = root.path("status")
//                         .asText("");

//         if (!"1".equals(status)) {

//             log.warn(
//                     "Tally responded with non-success status for company {}: {}",
//                     companyName,
//                     root
//             );

//             throw new ResponseStatusException(
//                     HttpStatus.BAD_GATEWAY,
//                     "Tally reported an error for company '"
//                     + companyName
//                     + "'. Raw response: "
//                     + root
//             );
//         }

//         JsonNode collection
//                 = root.path("data")
//                         .path("collection");

//         List<SalesVoucherDTO> vouchers
//                 = new ArrayList<>();

//         if (!collection.isArray()) {

//             log.warn(
//                     "Tally collection is not an array for company: {}",
//                     companyName
//             );

//             return vouchers;
//         }

//         // =====================================================
//         // MAP EVERY VOUCHER RETURNED BY THE SALES COLLECTION
//         // =====================================================
//         for (JsonNode voucherNode : collection) {

//             try {

//                 SalesVoucherDTO voucher
//                         = mapVoucher(voucherNode);

//                 if (voucher != null) {
//                     vouchers.add(voucher);
//                 }

//             } catch (Exception e) {

//                 log.warn(
//                         "Could not map sales voucher: {}",
//                         voucherNode,
//                         e
//                 );
//             }
//         }

//         log.info(
//                 "Fetched {} sales vouchers from company: {}",
//                 vouchers.size(),
//                 companyName
//         );

//         return vouchers;
//     }

//     // =========================================================
//     // MAP VOUCHER
//     // =========================================================
//     private SalesVoucherDTO mapVoucher(
//             JsonNode voucherNode
//     ) {

//         String voucherNumber
//                 = extractValue(
//                         voucherNode.path(
//                                 "vouchernumber"
//                         )
//                 );

//         // -----------------------------------------------------
//         // MAP INVENTORY ITEMS
//         // -----------------------------------------------------
//         List<SalesVoucherItemDTO> items
//                 = mapItems(
//                         voucherNode.path(
//                                 "allinventoryentries"
//                         )
//                 );

//         // -----------------------------------------------------
//         // GET ACTUAL VOUCHER TOTAL
//         //
//         // 1. Try party ledger amount
//         // 2. If unavailable, calculate from item amounts
//         // -----------------------------------------------------
//         BigDecimal partyLedgerAmount
//                 = extractPartyLedgerAmount(
//                         voucherNode
//                 );

//         BigDecimal totalAmount;

//         if (partyLedgerAmount != null
//                 && partyLedgerAmount.compareTo(
//                         BigDecimal.ZERO
//                 ) != 0) {

//             totalAmount
//                     = partyLedgerAmount.abs();

//             log.debug(
//                     "Voucher {} total from party ledger = {}",
//                     voucherNumber,
//                     totalAmount
//             );

//         } else {

//             totalAmount
//                     = calculateTotalAmount(
//                             items
//                     );

//             log.debug(
//                     "Voucher {} total calculated from inventory = {}",
//                     voucherNumber,
//                     totalAmount
//             );
//         }

//         // -----------------------------------------------------
//         // BUILD DTO
//         // -----------------------------------------------------
//         return SalesVoucherDTO.builder()
//                 .date(
//                         extractValue(
//                                 voucherNode.path(
//                                         "date"
//                                 )
//                         )
//                 )
//                 .voucherTypeName(
//                         extractValue(
//                                 voucherNode.path(
//                                         "vouchertypename"
//                                 )
//                         )
//                 )
//                 .voucherNumber(
//                         voucherNumber
//                 )
//                 .partyLedgerName(
//                         extractValue(
//                                 voucherNode.path(
//                                         "partyledgername"
//                                 )
//                         )
//                 )
//                 .guid(
//                         extractValue(
//                                 voucherNode.path(
//                                         "guid"
//                                 )
//                         )
//                 )
//                 .masterId(
//                         extractValue(
//                                 voucherNode.path(
//                                         "masterid"
//                                 )
//                         )
//                 )
//                 .totalAmount(
//                         totalAmount
//                 )
//                 .items(
//                         items
//                 )
//                 .build();
//     }

//     // =========================================================
//     // CALCULATE TOTAL FROM INVENTORY ITEMS
//     // =========================================================
//     private BigDecimal calculateTotalAmount(
//             List<SalesVoucherItemDTO> items
//     ) {

//         if (items == null
//                 || items.isEmpty()) {

//             return BigDecimal.ZERO;
//         }

//         BigDecimal total
//                 = BigDecimal.ZERO;

//         for (SalesVoucherItemDTO item : items) {

//             if (item == null) {
//                 continue;
//             }

//             BigDecimal amount
//                     = item.getAmount();

//             if (amount == null) {
//                 continue;
//             }

//             total
//                     = total.add(amount);
//         }

//         return total;
//     }

//     // =========================================================
//     // EXTRACT PARTY LEDGER TOTAL
//     // =========================================================
//     private BigDecimal extractPartyLedgerAmount(
//             JsonNode voucherNode
//     ) {

//         JsonNode ledgerEntries
//                 = voucherNode.path(
//                         "ledgerentries"
//                 );

//         if (!ledgerEntries.isArray()) {

//             log.debug(
//                     "ledgerentries not available for voucher {}",
//                     extractValue(
//                             voucherNode.path(
//                                     "vouchernumber"
//                             )
//                     )
//             );

//             return null;
//         }

//         for (JsonNode ledgerEntry
//                 : ledgerEntries) {

//             boolean isPartyLedger
//                     = ledgerEntry
//                             .path("ispartyledger")
//                             .asBoolean(false);

//             if (!isPartyLedger) {
//                 continue;
//             }

//             String amount
//                     = extractValue(
//                             ledgerEntry.path(
//                                     "amount"
//                             )
//                     );

//             BigDecimal value
//                     = toBigDecimal(amount);

//             if (value != null) {

//                 log.debug(
//                         "Party ledger amount for voucher {} = {}",
//                         extractValue(
//                                 voucherNode.path(
//                                         "vouchernumber"
//                                 )
//                         ),
//                         value
//                 );

//                 return value;
//             }
//         }

//         return null;
//     }

//     // =========================================================
//     // MAP INVENTORY ITEMS
//     // =========================================================
//     private List<SalesVoucherItemDTO> mapItems(
//             JsonNode itemsNode
//     ) {

//         List<SalesVoucherItemDTO> items
//                 = new ArrayList<>();

//         if (!itemsNode.isArray()) {
//             return items;
//         }

//         for (JsonNode itemNode
//                 : itemsNode) {

//             String stockItemName
//                     = extractValue(
//                             itemNode.path(
//                                     "stockitemname"
//                             )
//                     );

//             String rate
//                     = extractValue(
//                             itemNode.path(
//                                     "rate"
//                             )
//                     );

//             String amount
//                     = extractValue(
//                             itemNode.path(
//                                     "amount"
//                             )
//                     );

//             String quantity
//                     = extractValue(
//                             itemNode.path(
//                                     "actualqty"
//                             )
//                     );

//             RateParts rateParts
//                     = splitRate(rate);

//             QuantityParts quantityParts
//                     = splitQuantity(quantity);

//             SalesVoucherItemDTO item
//                     = SalesVoucherItemDTO.builder()
//                             .stockItemName(
//                                     stockItemName
//                             )
//                             .rate(
//                                     toBigDecimal(
//                                             rateParts.value
//                                     )
//                             )
//                             .rateUnit(
//                                     rateParts.unit
//                             )
//                             .amount(
//                                     toBigDecimal(
//                                             amount
//                                     )
//                             )
//                             .quantity(
//                                     toBigDecimal(
//                                             quantityParts.value
//                                     )
//                             )
//                             .quantityUnit(
//                                     quantityParts.unit
//                             )
//                             .build();

//             items.add(item);
//         }

//         return items;
//     }

//     // =========================================================
//     // SPLIT RATE
//     // =========================================================
//     private RateParts splitRate(
//             String rate
//     ) {

//         if (rate == null
//                 || rate.isBlank()) {

//             return new RateParts(
//                     null,
//                     null
//             );
//         }

//         String cleaned
//                 = rate.trim();

//         String[] parts
//                 = cleaned.split(
//                         "/",
//                         2
//                 );

//         String value
//                 = parts.length > 0
//                         ? parts[0].trim()
//                         : null;

//         String unit
//                 = parts.length > 1
//                         ? parts[1].trim()
//                         : null;

//         return new RateParts(
//                 value,
//                 unit
//         );
//     }

//     // =========================================================
//     // SPLIT QUANTITY
//     // =========================================================
//     private QuantityParts splitQuantity(
//             String quantity
//     ) {

//         if (quantity == null
//                 || quantity.isBlank()) {

//             return new QuantityParts(
//                     null,
//                     null
//             );
//         }

//         String cleaned
//                 = quantity.trim();

//         Pattern pattern
//                 = Pattern.compile(
//                         "^([+-]?\\d+(?:\\.\\d+)?)\\s*(.*)$"
//                 );

//         Matcher matcher
//                 = pattern.matcher(cleaned);

//         if (!matcher.matches()) {

//             return new QuantityParts(
//                     null,
//                     null
//             );
//         }

//         String value
//                 = matcher.group(1);

//         String unit
//                 = matcher.group(2);

//         if (unit != null) {
//             unit = unit.trim();
//         }

//         return new QuantityParts(
//                 value,
//                 unit
//         );
//     }

//     // =========================================================
//     // EXTRACT TALLY VALUE
//     // =========================================================
//     private String extractValue(
//             JsonNode node
//     ) {

//         if (node == null
//                 || node.isMissingNode()
//                 || node.isNull()) {

//             return null;
//         }

//         // Tally JSONEX object:
//         //
//         // {
//         //     "value": "1000"
//         // }
//         if (node.isObject()
//                 && node.has("value")) {

//             return node
//                     .path("value")
//                     .asText(null);
//         }

//         // String or number
//         if (node.isTextual()
//                 || node.isNumber()) {

//             return node.asText();
//         }

//         return null;
//     }

//     // =========================================================
//     // STRING -> BIG DECIMAL
//     // =========================================================
//     private BigDecimal toBigDecimal(
//             String value
//     ) {

//         if (value == null
//                 || value.isBlank()) {

//             return null;
//         }

//         try {

//             return new BigDecimal(
//                     value.trim()
//             );

//         } catch (NumberFormatException e) {

//             log.warn(
//                     "Could not convert '{}' to BigDecimal",
//                     value
//             );

//             return null;
//         }
//     }

//     // =========================================================
//     // VALIDATE COMPANY NAME
//     // =========================================================
//     private void validateCompanyName(
//             String companyName
//     ) {

//         if (companyName == null
//                 || companyName.isBlank()) {

//             throw new ResponseStatusException(
//                     HttpStatus.BAD_REQUEST,
//                     "Company name cannot be empty"
//             );
//         }
//     }

//     // =========================================================
//     // RATE PARTS
//     // =========================================================
//     private static class RateParts {

//         private final String value;
//         private final String unit;

//         private RateParts(
//                 String value,
//                 String unit
//         ) {

//             this.value = value;
//             this.unit = unit;
//         }
//     }

//     // =========================================================
//     // QUANTITY PARTS
//     // =========================================================
//     private static class QuantityParts {

//         private final String value;
//         private final String unit;

//         private QuantityParts(
//                 String value,
//                 String unit
//         ) {

//             this.value = value;
//             this.unit = unit;
//         }
//     }

//     // =========================================================
//     // BUILD TALLY REQUEST
//     // COMPANY-WISE
//     // =========================================================
//     private TallyRequest buildAllSalesVouchersRequest(
//             String companyName
//     ) {

//         return TallyRequest.builder()
//                 .staticVariables(
//                         List.of(
//                                 new TallyRequest.StaticVariable(
//                                         "svExportFormat",
//                                         "jsonex"
//                                 ),
//                                 new TallyRequest.StaticVariable(
//                                         "svCurrentCompany",
//                                         companyName
//                                 )
//                         )
//                 )
//                 .tdlmessage(
//                         List.of(
//                                 TallyRequest.TdlMessage
//                                         .builder()
//                                         .definitions(
//                                                 List.of(
//                                                         TallyRequest.Definition
//                                                                 .builder()
//                                                                 .metadata(
//                                                                         Map.of(
//                                                                                 "name",
//                                                                                 "TSPL All Sales Vouchers",
//                                                                                 "type",
//                                                                                 "Collection"
//                                                                         )
//                                                                 )
//                                                                 .attributes(
//                                                                         List.of(
//                                                                                 // =================================================
//                                                                                 // TYPE
//                                                                                 // =================================================

//                                                                                 Map.of(
//                                                                                         "Type",
//                                                                                         "Vouchers:VoucherType"
//                                                                                 ),
//                                                                                 // =================================================
//                                                                                 // CHILD OF SALES
//                                                                                 // =================================================

//                                                                                 Map.of(
//                                                                                         "Child Of",
//                                                                                         "$$VchTypeSales"
//                                                                                 ),
//                                                                                 // =================================================
//                                                                                 // BELONGS TO = YES
//                                                                                 // =================================================

//                                                                                 Map.of(
//                                                                                         "Belongs To",
//                                                                                         "Yes"
//                                                                                 ),
//                                                                                 // =================================================
//                                                                                 // FETCH REQUIRED FIELDS
//                                                                                 // =================================================

//                                                                                 Map.of(
//                                                                                         "Native Method",
//                                                                                         "Date, VoucherTypeName, VoucherNumber, PartyLedgerName, GUID, MasterID, AllInventoryEntries.List, LedgerEntries.List"
//                                                                                 )
//                                                                         )
//                                                                 )
//                                                                 .build()
//                                                 )
//                                         )
//                                         .build()
//                         )
//                 )
//                 .build();
//     }

//     // =========================================================
//     // CALL TALLY
//     // =========================================================
//     private ResponseEntity<String> callTally(
//             TallyRequest requestBody
//     ) {

//         HttpHeaders headers
//                 = new HttpHeaders();

//         headers.setContentType(
//                 MediaType.APPLICATION_JSON
//         );

//         headers.set(
//                 "version",
//                 "1"
//         );

//         headers.set(
//                 "tallyrequest",
//                 "export"
//         );

//         headers.set(
//                 "type",
//                 "collection"
//         );

//         headers.set(
//                 "id",
//                 "TSPLAllSalesVouchers"
//         );

//         // =====================================================
//         // SERIALIZE REQUEST
//         // =====================================================
//         String jsonBody;

//         try {

//             jsonBody
//                     = objectMapper.writeValueAsString(
//                             requestBody
//                     );

//         } catch (Exception e) {

//             log.error(
//                     "Failed to serialize Tally request body",
//                     e
//             );

//             throw new ResponseStatusException(
//                     HttpStatus.INTERNAL_SERVER_ERROR,
//                     "Failed to build Tally request"
//             );
//         }

//         log.debug(
//                 "Sending request to Tally at {}",
//                 tallyBaseUrl
//         );

//         log.debug(
//                 "Tally request company: {}",
//                 extractCompanyFromRequest(
//                         requestBody
//                 )
//         );

//         HttpEntity<String> entity
//                 = new HttpEntity<>(
//                         jsonBody,
//                         headers
//                 );

//         // =====================================================
//         // SEND REQUEST TO TALLY
//         // =====================================================
//         try {

//             ResponseEntity<String> response
//                     = restTemplate.exchange(
//                             tallyBaseUrl,
//                             HttpMethod.POST,
//                             entity,
//                             String.class
//                     );

//             log.debug(
//                     "Tally response status: {}",
//                     response.getStatusCode()
//             );

//             return response;

//         } catch (ResourceAccessException e) {

//             log.error(
//                     "Could not reach TallyPrime at {}. "
//                     + "Check that TallyPrime and the connector are running.",
//                     tallyBaseUrl,
//                     e
//             );

//             throw new ResponseStatusException(
//                     HttpStatus.SERVICE_UNAVAILABLE,
//                     "Could not reach TallyPrime at "
//                     + tallyBaseUrl
//                     + ". Ensure TallyPrime and TallyAPIConnectorV2.0.exe are running."
//             );

//         } catch (RestClientResponseException e) {

//             log.error(
//                     "Tally returned an error response: {} - {}",
//                     e.getStatusCode(),
//                     e.getResponseBodyAsString()
//             );

//             throw new ResponseStatusException(
//                     HttpStatus.BAD_GATEWAY,
//                     "Tally returned an error: "
//                     + e.getResponseBodyAsString()
//             );
//         }
//     }

//     // =========================================================
//     // EXTRACT COMPANY FOR LOGGING
//     // =========================================================
//     private String extractCompanyFromRequest(
//             TallyRequest requestBody
//     ) {

//         try {

//             JsonNode node
//                     = objectMapper.valueToTree(
//                             requestBody
//                     );

//             JsonNode staticVariables
//                     = node.path(
//                             "staticVariables"
//                     );

//             if (staticVariables.isArray()) {

//                 for (JsonNode variable
//                         : staticVariables) {

//                     String name
//                             = variable.path("name")
//                                     .asText("");

//                     if ("svCurrentCompany".equals(
//                             name
//                     )) {

//                         return variable
//                                 .path("value")
//                                 .asText("");
//                     }
//                 }
//             }

//         } catch (Exception e) {

//             log.debug(
//                     "Could not extract company from Tally request"
//             );
//         }

//         return "unknown";
//     }
// }


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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TallyService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AgentRelayService agentRelayService;

    // =========================================================
    // TALLY CONFIGURATION
    // =========================================================

    @Value("${tally.base-url:http://127.0.0.1:9000}")
    private String tallyBaseUrl;

    @Value("${tally.company-name:}")
    private String tallyCompanyName;

    // =========================================================
    // DEFAULT COMPANY - RAW
    // =========================================================

    public JsonNode pullAllSalesVouchersRaw(String userId) {
        return pullAllSalesVouchersRaw(userId, tallyCompanyName);
    }

    // =========================================================
    // COMPANY-WISE - RAW
    // =========================================================

    public JsonNode pullAllSalesVouchersRaw(String userId, String companyName) {

        validateCompanyName(companyName);

        log.info(
                "Fetching raw sales vouchers for company: {} (user: {})",
                companyName,
                userId
        );

        ResponseEntity<String> response =
                callTally(
                        userId,
                        buildAllSalesVouchersRequest(companyName)
                );

        try {

            String body = response.getBody();

            if (body == null || body.isBlank()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Tally returned an empty response"
                );
            }

            return objectMapper.readTree(body);

        } catch (ResponseStatusException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Failed to parse Tally response for company: {}",
                    companyName,
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally returned an unparsable response"
            );
        }
    }

    // =========================================================
    // DEFAULT COMPANY
    // =========================================================

    public List<SalesVoucherDTO> pullAllSalesVouchers(String userId) {

        return pullAllSalesVouchers(
                userId,
                tallyCompanyName
        );
    }

    // =========================================================
    // COMPANY-WISE
    // =========================================================

    public List<SalesVoucherDTO> pullAllSalesVouchers(
            String userId,
            String companyName
    ) {

        validateCompanyName(companyName);

        log.info(
                "Fetching sales vouchers for company: {} (user: {})",
                companyName,
                userId
        );

        JsonNode root =
                pullAllSalesVouchersRaw(userId, companyName);

        String status =
                root.path("status")
                        .asText("");

        if (!"1".equals(status)) {

            log.warn(
                    "Tally returned non-success status for company {}: {}",
                    companyName,
                    root
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally reported an error for company '"
                            + companyName
                            + "'. Raw response: "
                            + root
            );
        }

        JsonNode collection =
                root.path("data")
                        .path("collection");

        List<SalesVoucherDTO> vouchers =
                new ArrayList<>();

        if (!collection.isArray()) {

            log.warn(
                    "Tally collection is not an array for company: {}",
                    companyName
            );

            return vouchers;
        }

        for (JsonNode voucherNode : collection) {

            try {

                SalesVoucherDTO voucher =
                        mapVoucher(voucherNode);

                if (voucher != null) {
                    vouchers.add(voucher);
                }

            } catch (Exception e) {

                log.error(
                        "Failed to map voucher for company {}: {}",
                        companyName,
                        voucherNode,
                        e
                );
            }
        }

        log.info(
                "Fetched {} sales vouchers from company: {}",
                vouchers.size(),
                companyName
        );

        return vouchers;
    }

    // =========================================================
    // MAP COMPLETE VOUCHER
    // =========================================================

    private SalesVoucherDTO mapVoucher(
            JsonNode voucherNode
    ) {

        if (voucherNode == null || voucherNode.isNull()) {
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
        // GST SUMMARY
        // -----------------------------------------------------

        SalesVoucherGSTDTO gstDetails =
                calculateGST(
                        voucherNode,
                        items,
                        ledgerEntries
                );

        // -----------------------------------------------------
        // TOTAL
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
        // BUILD RESPONSE
        // -----------------------------------------------------

        return SalesVoucherDTO.builder()

                .date(
                        extractValue(
                                voucherNode.path("date")
                        )
                )

                .voucherTypeName(
                        extractValue(
                                voucherNode.path("vouchertypename")
                        )
                )

                .voucherNumber(
                        voucherNumber
                )

                .partyLedgerName(
                        extractValue(
                                voucherNode.path("partyledgername")
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

                .items(
                        items
                )

                .ledgerEntries(
                        ledgerEntries
                )

                .gstDetails(
                        gstDetails
                )

                .build();
    }

    // =========================================================
    // GET FIRST AVAILABLE ARRAY
    // =========================================================

    private JsonNode firstArray(
            JsonNode parent,
            String... names
    ) {

        if (parent == null) {
            return objectMapper.createArrayNode();
        }

        for (String name : names) {

            JsonNode node = parent.path(name);

            if (node.isArray()) {
                return node;
            }
        }

        return objectMapper.createArrayNode();
    }

    // =========================================================
    // MAP INVENTORY ITEMS
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

            if (itemNode == null || itemNode.isNull()) {
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

            // -------------------------------------------------
            // RATE
            // -------------------------------------------------

            RateParts rateParts =
                    splitRate(rate);

            // -------------------------------------------------
            // QUANTITY
            // -------------------------------------------------

            QuantityParts quantityParts =
                    splitQuantity(quantity);

            // -------------------------------------------------
            // GST RATES
            // -------------------------------------------------

            List<SalesVoucherGSTRateDTO> gstRates =
                    mapGSTRates(
                            firstArray(
                                    itemNode,
                                    "ratedetails",
                                    "gst.ratedetails",
                                    "gstratedetails"
                            )
                    );

            // -------------------------------------------------
            // ACCOUNTING ALLOCATIONS
            // -------------------------------------------------

            List<SalesVoucherCategoryAllocationDTO>
                    allocations =
                    mapAccountingAllocations(
                            firstArray(
                                    itemNode,
                                    "accountingallocations",
                                    "accountingallocations.list"
                            )
                    );

            // -------------------------------------------------
            // ITEM DTO
            // -------------------------------------------------

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

                            .gstRates(
                                    gstRates
                            )

                            .allocations(
                                    allocations
                            )

                            .build();

            items.add(item);
        }

        return items;
    }

    // =========================================================
    // MAP GST RATE DETAILS
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
                                            "gstratevaluationtype"
                                    )
                            )
                    );

            String rate =
                    extractValue(
                            rateNode.path(
                                    "gstrate"
                            )
                    );

            result.add(
                    SalesVoucherGSTRateDTO.builder()

                            .dutyHead(
                                    dutyHead
                            )

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
    // MAP ITEM ACCOUNTING ALLOCATIONS
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
    // MAP CATEGORY ALLOCATIONS
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

                            .category(
                                    category
                            )

                            .amount(
                                    amount
                            )

                            .costCentreAllocations(
                                    costCentreAllocations
                            )

                            .build()
            );
        }

        return result;
    }

    // =========================================================
    // MAP COST CENTRE ALLOCATIONS
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

                            .name(
                                    name
                            )

                            .amount(
                                    amount
                            )

                            .build()
            );
        }

        return result;
    }

    // =========================================================
    // MAP LEDGER ENTRIES
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
                    isGSTLedger(
                            ledgerName
                    );

            // -------------------------------------------------
            // BILL ALLOCATIONS
            // -------------------------------------------------

            List<SalesVoucherBillAllocationDTO>
                    billAllocations =
                    mapBillAllocations(
                            firstArray(
                                    ledger,
                                    "billallocations",
                                    "billallocations.list"
                            )
                    );

            // -------------------------------------------------
            // CATEGORY ALLOCATIONS
            // -------------------------------------------------

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

                            .ledgerName(
                                    ledgerName
                            )

                            .amount(
                                    amount
                            )

                            .partyLedger(
                                    partyLedger
                            )

                            .gstLedger(
                                    gstLedger
                            )

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
    // MAP BILL ALLOCATIONS
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

                            .name(
                                    name
                            )

                            .billType(
                                    billType
                            )

                            .amount(
                                    amount
                            )

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

        // -----------------------------------------------------
        // GST LEDGERS
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // ITEM GST RATES
        // -----------------------------------------------------

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

                .applicable(
                        applicable
                )

                .cgst(
                        cgst
                )

                .sgst(
                        sgst
                )

                .igst(
                        igst
                )

                .cess(
                        cess
                )

                .stateCess(
                        stateCess
                )

                .build();
    }

    // =========================================================
    // CHECK GST LEDGER
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

            return node
                    .path("value")
                    .asText(null);
        }

        if (node.isTextual()
                || node.isNumber()) {

            return node.asText();
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
    // STRING -> BIG DECIMAL
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

        try {

            return new BigDecimal(
                    cleaned
            );

        } catch (NumberFormatException e) {

            log.warn(
                    "Could not convert '{}' to BigDecimal",
                    value
            );

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
                pattern.matcher(
                        cleaned
                );

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

    // =========================================================
    // BUILD TALLY REQUEST
    //
    // IMPORTANT:
    //
    // Type        : Vouchers : VoucherType
    // Child of    : $$VchTypeSales
    // Belongs To  : Yes
    //
    // This is what allows voucher types which belong to
    // Sales, including custom Sales voucher types.
    // =========================================================

    private TallyRequest buildAllSalesVouchersRequest(
            String companyName
    ) {

        List<TallyRequest.StaticVariable> staticVariables =
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
        // COLLECTION DEFINITION
        // -----------------------------------------------------

        Map<String, String> metadata =
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

                        .metadata(
                                metadata
                        )

                        .attributes(
                                attributes
                        )

                        .build();

        TallyRequest.TdlMessage message =
                TallyRequest.TdlMessage.builder()

                        .definitions(
                                List.of(
                                        definition
                                )
                        )

                        .build();

        return TallyRequest.builder()

                .staticVariables(
                        staticVariables
                )

                .tdlmessage(
                        List.of(
                                message
                        )
                )

                .build();
    }

    // =========================================================
    // CALL TALLY
    // =========================================================

    private ResponseEntity<String> callTally(
            String userId,
            TallyRequest requestBody
    ) {

        // NOTE: this used to call restTemplate.exchange(tallyBaseUrl, ...)
        // directly from the backend server. That only worked when Tally
        // happened to be reachable from wherever the backend runs (e.g.
        // everything on localhost during dev). In the real deployment,
        // TallyPrime runs on the client's PC behind their own network,
        // so the backend must route through that user's Tally Agent
        // over the already-authenticated WebSocket connection instead.

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "version", "1",
                "tallyrequest", "export",
                "type", "collection",
                "id", "TSPLAllSalesVouchers"
        );

        String jsonBody;

        try {

            jsonBody =
                    objectMapper.writeValueAsString(
                            requestBody
                    );

        } catch (Exception e) {

            log.error(
                    "Failed to serialize Tally request body",
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to build Tally request"
            );
        }

        log.debug(
                "Relaying Tally request through agent for user {} (company: {})",
                userId,
                extractCompanyFromRequest(
                        requestBody
                )
        );

        AgentRelayService.RelayResponse response =
                agentRelayService.relay(
                        userId,
                        "POST",
                        headers,
                        jsonBody
                );

        log.debug(
                "Tally response status (via agent): {}",
                response.status()
        );

        return ResponseEntity
                .status(response.status())
                .body(response.body());
    }

    // =========================================================
    // EXTRACT COMPANY FROM REQUEST
    // =========================================================

    private String extractCompanyFromRequest(
            TallyRequest requestBody
    ) {

        try {

            JsonNode node =
                    objectMapper.valueToTree(
                            requestBody
                    );

            JsonNode staticVariables =
                    node.path(
                            "static_variables"
                    );

            if (staticVariables.isArray()) {

                for (JsonNode variable :
                        staticVariables) {

                    String name =
                            variable
                                    .path("name")
                                    .asText("");

                    if ("svCurrentCompany".equals(
                            name
                    )) {

                        return variable
                                .path("value")
                                .asText("");
                    }
                }
            }

        } catch (Exception e) {

            log.debug(
                    "Could not extract company from Tally request"
            );
        }

        return "unknown";
    }
}