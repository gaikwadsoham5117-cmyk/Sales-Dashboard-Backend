package com.example.SalesDashboard.tally.Sales.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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

        private Map<String, Object> metadata;

        /*
         * Used by "Collection" type definitions
         * (Type, Child Of, Belongs To, Fetch, Filters, ...).
         *
         * Left null (and omitted from JSON, see @JsonInclude
         * below) for "System"/"Formulae" definitions, which use
         * "value" instead.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<Map<String, String>> attributes;

        /*
         * Used by "System"/"Formulae" definitions to carry the
         * raw TDL expression, e.g.:
         *
         * "$Date >= ($$Date:\"02-04-2026\") AND $Date <= ($$Date:\"02-04-2026\")"
         *
         * Left null (and omitted from JSON) for "Collection"
         * type definitions, which use "attributes" instead.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String value;
    }
}