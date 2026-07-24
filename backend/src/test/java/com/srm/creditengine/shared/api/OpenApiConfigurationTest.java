package com.srm.creditengine.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {
    @Test
    void loginGetDoesNotAdvertiseARequestMediaTypeFailure() {
        var operation = new Operation().responses(new ApiResponses());
        var openApi = new OpenAPI().paths(new Paths().addPathItem(
                "/api/v1/auth/login",
                new PathItem().get(operation)));

        new OpenApiConfiguration().creditEngineOperationContracts().customise(openApi);

        assertThat(operation.getSecurity()).isEmpty();
        assertThat(operation.getResponses())
                .containsKeys("400", "401", "405", "406", "429", "500")
                .doesNotContainKey("415");
        assertThat(operation.getResponses().get("429").get$ref())
                .isEqualTo("#/components/responses/TooManyRequests");
    }
}
