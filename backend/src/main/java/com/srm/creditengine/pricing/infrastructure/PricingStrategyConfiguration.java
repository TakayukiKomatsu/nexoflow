package com.srm.creditengine.pricing.infrastructure;

import com.srm.creditengine.pricing.domain.ChequePricingStrategy;
import com.srm.creditengine.pricing.domain.InvoicePricingStrategy;
import com.srm.creditengine.pricing.domain.PricingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PricingStrategyConfiguration {
    @Bean
    PricingStrategy invoicePricingStrategy() {
        return new InvoicePricingStrategy();
    }

    @Bean
    PricingStrategy chequePricingStrategy() {
        return new ChequePricingStrategy();
    }
}
