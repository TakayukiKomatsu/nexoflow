package com.srm.creditengine.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ExchangeRateReferenceTest {
    @Autowired DataSource dataSource;

    @Test
    void exchangeRatesUseBaseAndQuoteCurrencies() throws Exception {
        try (Connection connection = dataSource.getConnection();
             var result = connection.createStatement().executeQuery("select count(*) from exchange_rates")) {
            result.next();
            assertThat(result.getInt(1)).isEqualTo(0);
        }
    }
}
