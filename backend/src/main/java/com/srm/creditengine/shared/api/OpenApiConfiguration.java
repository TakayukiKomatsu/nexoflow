package com.srm.creditengine.shared.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {
    private static final String BEARER_AUTH = "bearerAuth";
    private static final String PROBLEM_SCHEMA = "#/components/schemas/Problem";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final Map<String, ErrorResponseDefinition> ERROR_RESPONSES = Map.ofEntries(
            Map.entry("400", new ErrorResponseDefinition("BadRequest", "The request is invalid.")),
            Map.entry("401", new ErrorResponseDefinition("Unauthorized", "Authentication failed or is required.")),
            Map.entry("403", new ErrorResponseDefinition("Forbidden", "The authenticated actor is not authorized.")),
            Map.entry("404", new ErrorResponseDefinition("NotFound", "The requested resource was not found.")),
            Map.entry("405", new ErrorResponseDefinition("MethodNotAllowed", "The request method is not supported.")),
            Map.entry("406", new ErrorResponseDefinition("NotAcceptable", "The requested response media type is unavailable.")),
            Map.entry("409", new ErrorResponseDefinition("Conflict", "The request conflicts with current state.")),
            Map.entry("415", new ErrorResponseDefinition("UnsupportedMediaType", "The request media type is unsupported.")),
            Map.entry("422", new ErrorResponseDefinition("UnprocessableEntity", "The request cannot be processed.")),
            Map.entry("429", new ErrorResponseDefinition("TooManyRequests", "The request rate limit was exceeded.")),
            Map.entry("500", new ErrorResponseDefinition("InternalError", "An unexpected server error occurred.")),
            Map.entry("503", new ErrorResponseDefinition("ServiceUnavailable", "A required dependency is unavailable.")));

    @Bean
    OpenAPI creditEngineOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SRM Credit Engine")
                .version("v1")
                .description("Multi-currency receivable pricing and settlement."))
                .components(components());
    }

    @Bean
    OpenApiCustomizer creditEngineOperationContracts() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    boolean login = LOGIN_PATH.equals(path);
                    operation.setSecurity(login
                            ? List.of()
                            : List.of(new SecurityRequirement().addList(BEARER_AUTH)));
                    addStandardErrorResponses(path, method, operation, login);
                }));
    }

    private Components components() {
        var components = new Components()
                .addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                .addSchemas("Violation", violationSchema())
                .addSchemas("Problem", problemSchema());
        ERROR_RESPONSES.values().forEach(definition -> components.addResponses(
                definition.componentName(), problemResponse(definition.description())));
        return components;
    }

    private Schema<?> violationSchema() {
        var violation = new ObjectSchema();
        violation.addProperty("field", new StringSchema());
        violation.addProperty("message", new StringSchema());
        violation.setRequired(List.of("field", "message"));
        return violation;
    }

    private Schema<?> problemSchema() {
        var problem = new ObjectSchema();
        problem.addProperty("type", new StringSchema().format("uri"));
        problem.addProperty("title", new StringSchema());
        problem.addProperty("status", new IntegerSchema().format("int32"));
        problem.addProperty("detail", new StringSchema());
        problem.addProperty("instance", new StringSchema().format("uri-reference"));
        problem.addProperty("code", new StringSchema());
        problem.addProperty("correlationId", new StringSchema());
        problem.addProperty(
                "violations",
                new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Violation")));
        problem.setRequired(List.of(
                "type", "title", "status", "detail", "instance", "code", "correlationId"));
        return problem;
    }

    private ApiResponse problemResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new io.swagger.v3.oas.models.media.Content().addMediaType(
                        "application/problem+json",
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref(PROBLEM_SCHEMA))));
    }

    private void addStandardErrorResponses(
            String path, io.swagger.v3.oas.models.PathItem.HttpMethod method, Operation operation, boolean login) {
        ApiResponses responses = operation.getResponses();
        addError(responses, "400");
        addError(responses, "401");
        addError(responses, "405");
        addError(responses, "406");
        addError(responses, "500");
        if (login) {
            addError(responses, "429");
            return;
        }

        addError(responses, "403");
        addError(responses, "404");
        if (method != io.swagger.v3.oas.models.PathItem.HttpMethod.GET) {
            addError(responses, "409");
            addError(responses, "415");
        }
        if (path.startsWith("/api/v1/conversions")
                || path.startsWith("/api/v1/exchange-rates")
                || path.startsWith("/api/v1/fx-sync")
                || path.startsWith("/api/v1/pricing-")
                || path.startsWith("/api/v1/settlement")) {
            addError(responses, "422");
        }
        if (path.startsWith("/api/v1/fx-sync")) {
            addError(responses, "503");
        }
    }

    private void addError(ApiResponses responses, String status) {
        ErrorResponseDefinition definition = ERROR_RESPONSES.get(status);
        responses.putIfAbsent(
                status,
                new ApiResponse().$ref("#/components/responses/" + definition.componentName()));
    }

    private record ErrorResponseDefinition(String componentName, String description) {}
}
