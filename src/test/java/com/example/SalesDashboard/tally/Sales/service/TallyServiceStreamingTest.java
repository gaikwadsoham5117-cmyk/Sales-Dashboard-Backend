package com.example.SalesDashboard.tally.Sales.service;

import com.example.SalesDashboard.agent.service.AgentRelayService;
import com.example.SalesDashboard.tally.Sales.dto.SalesVoucherDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TallyServiceStreamingTest {

    @Mock
    private AgentRelayService agentRelayService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TallyService tallyService;

    @BeforeEach
    void setUp() {
        tallyService = new TallyService(
                objectMapper,
                agentRelayService,
                "Demo Company",
                4,
                10,
                60
        );
    }

    @Test
    void testStreamingParserProcessesVouchersIncrementally() throws Exception {
        // Build JSON with 50 vouchers in data.collection
        StringBuilder sb = new StringBuilder();
        sb.append("{\"status\":\"1\",\"data\":{\"collection\":[");
        for (int i = 1; i <= 50; i++) {
            if (i > 1) sb.append(",");
            sb.append(String.format("""
                    {
                        "date": "2026-03-01",
                        "vouchertypename": "Sales",
                        "vouchernumber": "INV-%03d",
                        "partyledgername": "Customer %d",
                        "partyparentname": "Sundry Debtors",
                        "allinventoryentries": [
                            {
                                "stockitemname": "Widget A",
                                "itemparentname": "Finished Goods",
                                "amount": "-1000.00",
                                "actualqty": "10.00 Pcs",
                                "billedqty": "10.00 Pcs",
                                "rate": "100.00/Pcs"
                            }
                        ]
                    }
                    """, i, i));
        }
        sb.append("]}}");

        String jsonPayload = sb.toString();

        when(agentRelayService.relay(eq("user-1"), eq("POST"), anyMap(), anyString()))
                .thenAnswer(invocation -> AgentRelayService.RelayResponse.inMemory(200, jsonPayload));

        List<SalesVoucherDTO> vouchers = tallyService.pullSalesVouchersByDateRange(
                "user-1",
                "Demo Company",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 15)
        );

        assertNotNull(vouchers);
        assertEquals(50, vouchers.size());
        assertEquals("INV-001", vouchers.get(0).getVoucherNumber());
        assertEquals("INV-050", vouchers.get(49).getVoucherNumber());
        assertEquals("Customer 1", vouchers.get(0).getPartyLedgerName());
        assertEquals("Sundry Debtors", vouchers.get(0).getPartyParentName());
        assertEquals("Finished Goods", vouchers.get(0).getItems().get(0).getItemParentName());
    }

    @Test
    void testAuxiliaryItemParentCachingAvoidsRedundantCalls() throws Exception {
        // First response has an item without itemparentname, so it needs enrichment
        String voucherJson = """
                {
                    "status": "1",
                    "data": {
                        "collection": [
                            {
                                "date": "2026-03-01",
                                "vouchertypename": "Sales",
                                "vouchernumber": "INV-101",
                                "partyledgername": "Customer 101",
                                "allinventoryentries": [
                                    {
                                        "stockitemname": "Custom Widget",
                                        "amount": "-500.00"
                                    }
                                ]
                            }
                        ]
                    }
                }
                """;

        String parentJson = """
                {
                    "status": "1",
                    "data": {
                        "collection": [
                            {
                                "stockitemname": "Custom Widget",
                                "itemparentname": "Manufactured Products"
                            }
                        ]
                    }
                }
                """;

        AtomicInteger parentFetchCount = new AtomicInteger(0);

        when(agentRelayService.relay(eq("user-1"), eq("POST"), anyMap(), anyString()))
                .thenAnswer(invocation -> {
                    Map<String, String> headers = invocation.getArgument(2);
                    String collectionId = headers.get("id");
                    if ("TSPLStockItemParents".equals(collectionId)) {
                        parentFetchCount.incrementAndGet();
                        return AgentRelayService.RelayResponse.inMemory(200, parentJson);
                    }
                    return AgentRelayService.RelayResponse.inMemory(200, voucherJson);
                });

        // Slice 1: should fetch item parent from Tally
        List<SalesVoucherDTO> slice1 = tallyService.pullSalesVouchersByDateRange(
                "user-1", "Demo Company",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 7)
        );
        assertEquals(1, slice1.size());
        assertEquals("Manufactured Products", slice1.get(0).getItems().get(0).getItemParentName());
        assertEquals(1, parentFetchCount.get(), "First call should query Tally for parent");

        // Slice 2: same company and item, should use CACHE and NOT call Tally for parent
        List<SalesVoucherDTO> slice2 = tallyService.pullSalesVouchersByDateRange(
                "user-1", "Demo Company",
                LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 14)
        );
        assertEquals(1, slice2.size());
        assertEquals("Manufactured Products", slice2.get(0).getItems().get(0).getItemParentName());
        assertEquals(1, parentFetchCount.get(), "Second call should use cached parent mapping without calling Tally!");
    }
}
