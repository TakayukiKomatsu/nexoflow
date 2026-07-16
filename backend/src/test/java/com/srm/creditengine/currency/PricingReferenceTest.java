package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PricingReferenceTest {
    @Autowired DataSource dataSource;

    @Test
    void seedsInvoiceAndChequeRiskSpreads() throws Exception {
        try (Connection connection = dataSource.getConnection();
             var result = connection.createStatement().executeQuery("select count(*) from product_spread_versions")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(2);
        }
    }
}
