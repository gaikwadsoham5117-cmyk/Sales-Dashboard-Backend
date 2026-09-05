package com.example.SalesDashboard.tally.Company.service;

import com.example.SalesDashboard.agent.service.AgentRelayService;

import com.example.SalesDashboard.tally.Company.dto.CompanyDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);
    private static final String COMPANY_COLLECTION_ID = "List of Companies";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRelayService agentRelayService;

    @Value("${tally.import-format:jsonex}")
    private String tallyExportFormat;

    public CompanyService(AgentRelayService agentRelayService) {
        this.agentRelayService = agentRelayService;
    }

    public String pullAllCompaniesRaw(String userId) {
        ObjectNode payload = buildExportPayload();

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "version", "1",
                "tallyrequest", "export",
                "type", "collection",
                "id", COMPANY_COLLECTION_ID
        );

        return sendToTally(userId, headers, payload);
    }

    public List<CompanyDto> pullAllCompanies(String userId) {
        String rawResponse = pullAllCompaniesRaw(userId);
        return parseCompanyCollection(rawResponse);
    }

    
    private ObjectNode buildExportPayload() {
        ObjectNode staticVar = objectMapper.createObjectNode();
        staticVar.put("name", "svExportFormat");
        staticVar.put("value", tallyExportFormat);

        ObjectNode root = objectMapper.createObjectNode();
        // Deliberately no svCurrentCompany here - this lists companies,
        // it isn't scoped to one already-selected company.
        root.putArray("static_variables").add(staticVar);

        return root;
    }

    private List<CompanyDto> parseCompanyCollection(String rawJson) {
        List<CompanyDto> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode collection = root.path("data").path("collection");

            if (!collection.isArray()) {
                log.warn("Tally response had no 'data.collection' array: {}", rawJson);
                return results;
            }

            for (JsonNode c : collection) {
                String name = firstNonBlank(
                        textOrNull(c.path("metadata").path("name")),
                        textOrNull(c.path("name").path("value")),
                        textOrNull(c.path("name"))
                );
                String guid = textOrNull(c.path("guid").path("value"));
                String startingFrom = textOrNull(c.path("startingfrom").path("value"));
                String endingAt = textOrNull(c.path("endingat").path("value"));

               results.add(new CompanyDto(name));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Tally company list response", e);
        }
        return results;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return text.isBlank() ? null : text;
    }

    private String sendToTally(String userId, Map<String, String> headers, Object payload) {
        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize payload to JSON", e);
        }

        log.debug("Relaying company list request through agent for user {}", userId);

        AgentRelayService.RelayResponse response =
                agentRelayService.relay(userId, "POST", headers, jsonBody);

        log.debug("Tally company list response status (via agent): {}", response.status());

        return response.body();
    }
}