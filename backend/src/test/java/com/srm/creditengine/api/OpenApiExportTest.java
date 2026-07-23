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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {
    @Autowired MockMvc mvc;

    @Test
    void exportsTheCanonicalDocumentServedByTheRuntimeEndpoint() throws Exception {
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        ObjectMapper reader = new ObjectMapper();
        JsonNode document = reader.readTree(response);

        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("paths").has("/api/v1/pricing-quotes")).isTrue();
        assertThat(document.path("components").path("schemas").has("PricingBreakdownResponse"))
                .isTrue();

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
}
