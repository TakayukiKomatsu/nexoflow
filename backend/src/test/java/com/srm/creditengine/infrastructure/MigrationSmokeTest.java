package com.srm.creditengine.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MigrationSmokeTest {
    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesSchemaHistory() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection.createStatement().executeQuery(
                        "select count(*) from \"flyway_schema_history\"")) {
            result.next();
            assertThat(result.getInt(1)).isGreaterThan(0);
        }
    }
}
