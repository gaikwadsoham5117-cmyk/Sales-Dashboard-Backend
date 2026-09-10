package com.example.SalesDashboard.tally.Company.service;

import com.example.SalesDashboard.agent.service.AgentRelayService;
import com.example.SalesDashboard.tally.Company.dto.CompanyDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final String COMPANY_COLLECTION_ID = "List of Companies";

    private final ObjectMapper objectMapper;
    private final AgentRelayService agentRelayService;

    @Value("${tally.import-format:jsonex}")
    private String tallyExportFormat;

    public List<CompanyDto> pullAllCompanies(String userId) {

        ObjectNode payload = buildExportPayload();

        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "version", "1",
                "tallyrequest", "export",
                "type", "collection",
                "id", COMPANY_COLLECTION_ID
        );

        String jsonBody;

        try {
            jsonBody = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to prepare Tally company request"
            );
        }

        AgentRelayService.RelayResponse response;

        try {
            response = agentRelayService.relay(
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
                    "Failed to communicate with Tally Agent"
            );
        }

        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Empty response received from Tally Agent"
            );
        }

        if (response.status() < 200 || response.status() >= 300) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Tally Agent returned HTTP status " + response.status()
            );
        }

        String rawResponse = response.body();

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Empty response received from Tally"
            );
        }

        return parseCompanyCollection(rawResponse);
    }


    private ObjectNode buildExportPayload() {

        ObjectNode staticVariable =
                objectMapper.createObjectNode();

        staticVariable.put("name", "svExportFormat");
        staticVariable.put("value", tallyExportFormat);

        ObjectNode root =
                objectMapper.createObjectNode();

        root.putArray("static_variables")
                .add(staticVariable);

        return root;
    }


    private List<CompanyDto> parseCompanyCollection(
            String rawJson
    ) {

        List<CompanyDto> results = new ArrayList<>();

        try {

            JsonNode root =
                    objectMapper.readTree(rawJson);

            JsonNode collection =
                    root.path("data").path("collection");

            if (!collection.isArray()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Invalid company response received from Tally"
                );
            }

            for (JsonNode companyNode : collection) {

                String name = firstNonBlank(
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
                                companyNode.path("name")
                        )
                );

                if (name != null) {

                    results.add(
                            new CompanyDto(name)
                    );
                }
            }

            return results;

        } catch (ResponseStatusException e) {

            throw e;

        } catch (Exception e) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to parse Tally company response"
            );
        }
    }

    private String firstNonBlank(String... values) {

        for (String value : values) {

            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private String textOrNull(JsonNode node) {

        if (node == null ||
                node.isMissingNode() ||
                node.isNull()) {

            return null;
        }

        String text = node.asText();

        if (text == null || text.isBlank()) {
            return null;
        }

        return text.trim();
    }
}