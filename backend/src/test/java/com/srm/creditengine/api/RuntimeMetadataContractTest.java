package com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RuntimeMetadataContractTest {
    private static final List<String> providerRequests = new CopyOnWriteArrayList<>();
    private static HttpServer provider;

    @DynamicPropertySource
    static void configureFxProvider(DynamicPropertyRegistry properties) {
        startProvider();
        properties.add(
                "srm.fx-provider.base-url",
                () -> "http://127.0.0.1:" + provider.getAddress().getPort());
    }

    @AfterAll
    static void stopProvider() {
        if (provider != null) {
            provider.stop(0);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    @Qualifier("mockFxHealthIndicator")
    private HealthIndicator mockFxHealthIndicator;

    @Test
    void exposesOpenApiAndHealthProbes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SRM Credit Engine"));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }


    @Test
    void exposesQuoteMetadataOutsideThePricingBreakdownInOpenApi() throws Exception {
        String openApi = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode schemas = objectMapper.readTree(openApi).path("components").path("schemas");
        Map<String, JsonNode> quoteProperties = objectMapper.convertValue(
                schemas.path("QuoteResponse").path("properties"),
                new TypeReference<>() {});

        assertThat(quoteProperties).containsKeys(
                "id",
                "receivableId",
                "productType",
                "dueDate",
                "pricing",
                "expiresAt",
                "status",
                "createdBy");
        assertThat(quoteProperties.get("pricing").get("$ref").asText())
                .endsWith("/PricingBreakdownResponse");

        Map<String, JsonNode> pricingProperties = objectMapper.convertValue(
                schemas.path("PricingBreakdownResponse").path("properties"),
                new TypeReference<>() {});
        assertThat(pricingProperties).containsKeys(
                "faceAmount",
                "faceCurrency",
                "settlementCurrency",
                "baseRate",
                "spread",
                "strategyCode",
                "dayCountConvention",
                "termInMonths",
                "discountedAmount",
                "fxBaseCurrency",
                "fxQuoteCurrency",
                "fxRate",
                "fxSource",
                "fxObservedAt",
                "settlementAmount",
                "pricedAt");
        assertThat(pricingProperties).doesNotContainKeys("productType", "dueDate");
    }

    @Test
    void marksEveryAlwaysSerializedResponsePropertyAsRequiredInOpenApi() throws Exception {
        JsonNode schemas = new ObjectMapper()
                .readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("components")
                .path("schemas");

        for (String schemaName : List.of(
                "AccessToken",
                "CurrentUser",
                "EntryResponse",
                "ItemResponse",
                "PageResponse",
                "PreviewResponse",
                "PricingBreakdownResponse",
                "QuoteResponse",
                "Response",
                "SettlementResponse")) {
            JsonNode schema = schemas.path(schemaName);
            Set<String> properties = StreamSupport.stream(
                            ((Iterable<String>) () -> schema.path("properties").fieldNames()).spliterator(), false)
                    .collect(Collectors.toSet());
            Set<String> required = StreamSupport.stream(schema.path("required").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.toSet());

            assertThat(required)
                    .as("required properties for %s", schemaName)
                    .isEqualTo(properties);
        }

        assertThat(schemas.path("EntryResponse")
                        .path("properties")
                        .path("entryType")
                        .path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("SETTLEMENT", "REVERSAL");
        assertThat(schemas.path("CurrentUser")
                        .path("properties")
                        .path("roles")
                        .path("items")
                        .path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("OPERATOR", "ANALYST", "ADMIN", "AUDITOR");
    }

    @Test
    void documentsTheMonthlyReferenceRateDomainMaximumInOpenApi() throws Exception {
        JsonNode schemas = new ObjectMapper()
                .readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("components")
                .path("schemas");

        assertThat(schemas.path("BaseRateRequest").path("properties").path("monthlyRate").path("maximum").decimalValue())
                .isEqualByComparingTo("1.0000000000");
        assertThat(schemas.path("ProductSpreadRequest").path("properties").path("monthlySpread").path("maximum").decimalValue())
                .isEqualByComparingTo("1.0000000000");
    }

    @Test
    void documentsTheTenYearPricingTermBoundaryInOpenApi() throws Exception {
        JsonNode schemas = new ObjectMapper()
                .readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("components")
                .path("schemas");

        assertThat(schemas.path("SimulationRequest")
                        .path("properties")
                        .path("dueDate")
                        .path("description")
                        .asText())
                .contains("ten years");
    }

    @Test
    void documentsTheSettlementBatchLimitInOpenApi() throws Exception {
        JsonNode schemas = new ObjectMapper()
                .readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("components")
                .path("schemas");

        assertThat(schemas.path("QuoteIdsRequest")
                        .path("properties")
                        .path("quoteIds")
                        .path("maxItems")
                        .asInt())
                .isEqualTo(100);
    }

    @Test
    void oneFxProviderUrlDrivesTheAdapterAndBoundedReadinessHealth() throws Exception {
        providerRequests.clear();

        var health = mockFxHealthIndicator.health();
        Object synchronizationService = applicationContext.getBean("httpFxSynchronizationService");
        var synchronize = synchronizationService.getClass()
                .getDeclaredMethod("synchronize", String.class, String.class, String.class);
        synchronize.setAccessible(true);
        synchronize.invoke(synchronizationService, "BRL", "USD", "SYSTEM");

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).isEmpty();
        assertThat(providerRequests).containsExactlyInAnyOrder(
                "/health",
                "/api/v1/rates/BRL-USD");
    }

    private static void startProvider() {
        if (provider != null) {
            return;
        }
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.createContext("/health", exchange -> respond(exchange, "UP", "text/plain"));
            provider.createContext(
                    "/api/v1/rates/BRL-USD",
                    exchange -> respond(
                            exchange,
                            "{\"rate\":1.08,\"observedAt\":\"2026-07-18T00:00:00Z\",\"source\":\"TEST_PROVIDER\"}",
                            "application/json"));
            provider.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start test FX provider", exception);
        }
    }

    private static void respond(HttpExchange exchange, String body, String contentType) throws IOException {
        providerRequests.add(exchange.getRequestURI().getPath());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
