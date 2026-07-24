package com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("contract")
class OpenApiExportTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;

    @Test
    void exportsTheCanonicalDocumentServedByTheRuntimeEndpoint() throws Exception {
        assertThat(environment.matchesProfiles("contract")).isTrue();
        assertThat(environment.matchesProfiles("test")).isFalse();
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ObjectMapper reader = new ObjectMapper();
        JsonNode document = reader.readTree(response);

        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("paths").has("/api/v1/pricing-quotes")).isTrue();
        assertThat(document.path("paths").fieldNames())
                .toIterable()
                .allSatisfy(path -> assertThat(path).doesNotStartWith("/api/v1/runtime"));
        assertThat(document.path("components").path("schemas").has("PricingBreakdownResponse"))
                .isTrue();
        assertThat(document.at("/components/securitySchemes/bearerAuth/type").asText())
                .isEqualTo("http");
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText())
                .isEqualTo("bearer");
        assertThat(document.at("/components/securitySchemes/bearerAuth/bearerFormat").asText())
                .isEqualTo("JWT");
        assertOperationSecurityIsExplicit(document);

        JsonNode problem = document.at("/components/schemas/Problem");
        assertThat(java.util.stream.StreamSupport.stream(
                                problem.path("required").spliterator(), false)
                        .map(JsonNode::asText)
                        .toList())
                .containsExactlyInAnyOrder(
                        "type", "title", "status", "detail", "instance", "code", "correlationId");
        assertThat(problem.at("/properties/violations/items/$ref").asText())
                .isEqualTo("#/components/schemas/Violation");
        assertThat(document.at("/components/responses/BadRequest/content/application~1problem+json/schema/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/Problem");
        assertThat(document.at("/components/responses").fieldNames())
                .toIterable()
                .contains(
                        "BadRequest",
                        "Unauthorized",
                        "Forbidden",
                        "NotFound",
                        "MethodNotAllowed",
                        "NotAcceptable",
                        "Conflict",
                        "UnsupportedMediaType",
                        "UnprocessableEntity",
                        "TooManyRequests",
                        "InternalError",
                        "ServiceUnavailable");
        assertThat(document.at("/paths/~1api~1v1~1pricing-quotes/post/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(document.at("/paths/~1api~1v1~1pricing-quotes/post/responses/403/$ref").asText())
                .isEqualTo("#/components/responses/Forbidden");
        assertThat(document.at("/paths/~1api~1v1~1auth~1login/post/responses/429/$ref").asText())
                .isEqualTo("#/components/responses/TooManyRequests");
        assertThat(document.at("/paths/~1api~1v1~1auth~1login/post/responses/415/$ref").asText())
                .isEqualTo("#/components/responses/UnsupportedMediaType");
        assertDistinctMutationRequestSchemas(document);
        assertBoundedNonBlankStrings(document);
        assertLifecycleStatesAreClosed(document);
        assertCreatedSettlementResponses(document);

        String output = System.getProperty("srm.openapi.output", "");
        if (!output.isBlank()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> canonical = reader.readValue(response, Map.class);
            ObjectMapper writer = new ObjectMapper()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
            String json = writer.writerWithDefaultPrettyPrinter().writeValueAsString(canonical) + "\n";
            Path target = Path.of(output);
            Files.createDirectories(target.getParent());
            Files.writeString(target, json, StandardCharsets.UTF_8);
        }
    }

    private void assertBoundedNonBlankStrings(JsonNode document) {
        assertStringBounds(document, "AssignorRequest", "legalName", 1, 200);
        assertStringBounds(document, "AssignorRequest", "taxId", 1, 32);
        assertStringBounds(document, "ExchangeRateRequest", "source", 1, 50);
    }

    private void assertStringBounds(
            JsonNode document, String schemaName, String propertyName, int minimum, int maximum) {
        JsonNode property = document.at(
                "/components/schemas/" + schemaName + "/properties/" + propertyName);
        assertThat(property.path("minLength").asInt()).isEqualTo(minimum);
        assertThat(property.path("maxLength").asInt()).isEqualTo(maximum);
    }

    private void assertLifecycleStatesAreClosed(JsonNode document) {
        assertStringEnum(
                document,
                "ReceivableResponse",
                "status",
                "REGISTERED",
                "SETTLED",
                "REVERSED");
        assertStringEnum(document, "QuoteResponse", "status", "ACTIVE", "EXPIRED", "CONSUMED");
        assertStringEnum(document, "SettlementResponse", "status", "COMPLETED");
    }

    private void assertStringEnum(
            JsonNode document, String schemaName, String propertyName, String... expected) {
        JsonNode values = document.at(
                "/components/schemas/" + schemaName + "/properties/" + propertyName + "/enum");
        assertThat(java.util.stream.StreamSupport.stream(values.spliterator(), false)
                        .map(JsonNode::asText)
                        .toList())
                .containsExactlyInAnyOrder(expected);
    }

    private void assertDistinctMutationRequestSchemas(JsonNode document) {
        assertThat(document.at(
                                "/paths/~1api~1v1~1assignors/post/requestBody/content/application~1json/schema/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/AssignorRequest");
        assertThat(document.at(
                                "/paths/~1api~1v1~1receivables/post/requestBody/content/application~1json/schema/$ref")
                        .asText())
                .isEqualTo("#/components/schemas/ReceivableRequest");
        assertThat(document.at("/components/schemas/AssignorRequest/properties").fieldNames())
                .toIterable()
                .contains("legalName", "taxId", "active")
                .doesNotContain("assignorId", "faceAmount");
        assertThat(document.at("/components/schemas/ReceivableRequest/properties").fieldNames())
                .toIterable()
                .contains("assignorId", "productType", "faceAmount", "faceCurrency", "issueDate", "dueDate")
                .doesNotContain("legalName", "taxId");
    }

    private void assertCreatedSettlementResponses(JsonNode document) {
        Map<String, String> responsesByPointer = Map.of(
                "/paths/~1api~1v1~1settlements/post/responses", "#/components/schemas/SettlementResponse",
                "/paths/~1api~1v1~1settlements~1{settlementId}~1reversals/post/responses",
                        "#/components/schemas/ReversalResponse");
        for (Map.Entry<String, String> expected : responsesByPointer.entrySet()) {
            String pointer = expected.getKey();
            JsonNode responses = document.at(pointer);
            assertThat(responses.has("201")).as("201 response at %s", pointer).isTrue();
            assertThat(responses.has("200")).as("no false 200 response at %s", pointer).isFalse();
            assertThat(responses.at("/201/headers/Idempotent-Replay/schema/type").asText())
                    .isEqualTo("string");
            assertThat(responses.at("/201/headers/Idempotent-Replay/schema/enum/0").asText())
                    .isEqualTo("true");
            assertThat(responses.at("/201/content/application~1json/schema/$ref").asText())
                    .isEqualTo(expected.getValue());
        }
    }

    private void assertOperationSecurityIsExplicit(JsonNode document) {
        document.path("paths").properties().forEach(path ->
                path.getValue().properties().stream()
                        .filter(operation -> operation.getValue().has("responses"))
                        .forEach(operation -> {
                            JsonNode security = operation.getValue().path("security");
                            if (path.getKey().equals("/api/v1/auth/login")) {
                                assertThat(security.isArray() && security.isEmpty()).isTrue();
                            } else {
                                assertThat(security.at("/0/bearerAuth").isArray())
                                        .as("security for %s %s", operation.getKey(), path.getKey())
                                        .isTrue();
                            }
                        }));
    }
}
