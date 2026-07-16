package com.srm.creditengine.shared.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {
    @Bean
    OpenAPI creditEngineOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SRM Credit Engine")
                .version("v1")
                .description("Multi-currency receivable pricing and settlement."));
    }
}
