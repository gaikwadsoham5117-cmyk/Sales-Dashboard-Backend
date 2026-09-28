package com.example.SalesDashboard.tally.Company.service;

import com.example.SalesDashboard.agent.service.AgentRelayService;
import com.example.SalesDashboard.tally.Company.dto.CompanyDto;
import com.example.SalesDashboard.tally.Company.exception.CompanyAgentStatusException;
import com.example.SalesDashboard.tally.Company.exception.CompanyEmptyResponseException;
import com.example.SalesDashboard.tally.Company.exception.CompanyRequestPreparationException;
import com.example.SalesDashboard.tally.Company.exception.CompanyResponseParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final String COMPANY_COLLECTION_ID =
            "List of Companies";

    private final ObjectMapper objectMapper;
    private final AgentRelayService agentRelayService;

    @Value("${tally.import-format:jsonex}")
    private String tallyExportFormat;

    // ============================================================
    // GET COMPANIES
    // ============================================================

    public List<CompanyDto> pullAllCompanies(String userId) {

        ObjectNode payload = buildExportPayload();

        Map<String, String> headers = Map.of(
                "Content-Type",
                "application/json",
                "version",
                "1",
                "tallyrequest",
                "export",
                "type",
                "collection",
                "id",
                COMPANY_COLLECTION_ID
        );

        String jsonBody;

        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new CompanyRequestPreparationException("Failed to prepare Tally company request"
            );
        }


        AgentRelayService.RelayResponse response =
                agentRelayService.relay(
                        userId,
                        "POST",
                        headers,
                        jsonBody
                );

        if (response == null) {
            throw new CompanyEmptyResponseException("Empty response received from Tally Agent"
            );
        }

        if (response.status() < 200
                || response.status() >= 300) {

            throw new CompanyAgentStatusException("Tally Agent returned HTTP status "
                    + response.status()
            );
        }

        String rawResponse = response.body();

        if (rawResponse == null
                || rawResponse.isBlank()) {

            throw new CompanyEmptyResponseException("Empty response received from Tally"
            );
        }

        return parseCompanyCollection(rawResponse);
    }

    // ============================================================
    // PAYLOAD
    // ============================================================

    private ObjectNode buildExportPayload() {

        ObjectNode staticVariable =
                objectMapper.createObjectNode();

        staticVariable.put(
                "name",
                "svExportFormat"
        );

        staticVariable.put(
                "value",
                tallyExportFormat
        );

        ObjectNode root =
                objectMapper.createObjectNode();

        root.putArray(
                "static_variables"
        ).add(staticVariable);

        return root;
    }

    // ============================================================
    // PARSE
    // ============================================================

    private List<CompanyDto> parseCompanyCollection(
            String rawJson
    ) {

        List<CompanyDto> results =
                new ArrayList<>();

        try {

            JsonNode root =
                    objectMapper.readTree(rawJson);

            JsonNode collection =
                    root.path("data")
                            .path("collection");

            if (!collection.isArray()) {

                throw new CompanyResponseParseException("Invalid company response received from Tally"
                );
            }

            for (JsonNode companyNode : collection) {

                String name =
                        firstNonBlank(
                                textOrNull(
                                        companyNode
                                                .path("metadata")
                                                .path("name")
                                ),

                                textOrNull(
                                        companyNode
                                                .path("name")
                                                .path("value")
                                ),

                                textOrNull(
                                        companyNode
                                                .path("name")
                                )
                        );

                if (name != null) {

                    results.add(
                            new CompanyDto(name)
                    );
                }
            }

            return results;

        } catch (CompanyResponseParseException e) {

            throw e;

        } catch (Exception e) {

            throw new CompanyResponseParseException("Failed to parse Tally company response"
            );
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private String firstNonBlank(
            String... values
    ) {

        for (String value : values) {

            if (value != null
                    && !value.isBlank()) {

                return value.trim();
            }
        }

        return null;
    }

    private String textOrNull(
            JsonNode node
    ) {

        if (node == null
                || node.isMissingNode()
                || node.isNull()) {

            return null;
        }

        String text =
                node.asText();

        if (text == null
                || text.isBlank()) {

            return null;
        }

        return text.trim();
    }
}