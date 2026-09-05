package com.example.SalesDashboard.tally.Sales.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TallyRequest {

    @JsonProperty("static_variables")
    private List<StaticVariable> staticVariables;

    @JsonProperty("tdlmessage")
    private List<TdlMessage> tdlmessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StaticVariable {
        private String name;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TdlMessage {
        private List<Definition> definitions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Definition {
        private Map<String, String> metadata;
        private List<Map<String, String>> attributes;
    }
}