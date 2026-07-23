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
                        "Conflict",
                        "UnprocessableEntity",
                        "TooManyRequests",
                        "InternalError");
        assertThat(document.at("/paths/~1api~1v1~1pricing-quotes/post/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(document.at("/paths/~1api~1v1~1pricing-quotes/post/responses/403/$ref").asText())
                .isEqualTo("#/components/responses/Forbidden");
        assertThat(document.at("/paths/~1api~1v1~1auth~1login/post/responses/429/$ref").asText())
                .isEqualTo("#/components/responses/TooManyRequests");

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
