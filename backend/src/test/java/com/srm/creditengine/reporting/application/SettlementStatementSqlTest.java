package com.srm.creditengine.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementStatementSqlTest {
    private static final Instant FROM = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2030-02-01T00:00:00Z");
    private static final UUID ASSIGNOR =
            UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Test
    void rendersEveryActiveFilterFromTheProductionResourceInArgumentOrder() {
        var query = SettlementStatementSql.fromClasspath().render(
                new SettlementStatementService.Filter(
                        FROM,
                        TO,
                        ASSIGNOR,
                        "BRL",
                        "USD",
                        "MERCANTILE_INVOICE",
                        2,
                        25),
                26,
                50);

        assertThat(query.sql())
                .contains(
                        "effective_at >= ?",
                        "effective_at < ?",
                        "assignor_id = ?",
                        "asset_currency_code = ?",
                        "settlement_currency_code = ?",
                        "product_type_code = ?",
                        "limit ? offset ?")
                .doesNotContain("/*?", ":from", ":limit");
        assertThat(query.arguments())
                .containsExactly(
                        Timestamp.from(FROM),
                        Timestamp.from(TO),
                        ASSIGNOR,
                        "BRL",
                        "USD",
                        "MERCANTILE_INVOICE",
                        26,
                        50L);
    }

    @Test
    void removesInactiveFilterClausesWithoutLeavingParameters() {
        var query = SettlementStatementSql.fromClasspath().render(
                new SettlementStatementService.Filter(
                        null, null, null, null, null, null, 0, 50),
                51,
                0);

        assertThat(query.sql())
                .contains("where true", "limit ? offset ?")
                .doesNotContain(
                        "effective_at >=",
                        "effective_at <",
                        "assignor_id =",
                        "asset_currency_code =",
                        "settlement_currency_code =",
                        "product_type_code =",
                        "/*?",
                        ":from",
                        ":limit",
                        ":offset");
        assertThat(query.arguments()).containsExactly(51, 0L);
    }

    @Test
    void failsClearlyWhenTheProductionSqlResourceIsMissing() throws Exception {
        var resource = SettlementStatementSql.class
                .getClassLoader()
                .getResource(SettlementStatementSql.RESOURCE);
        assertThat(resource).isNotNull();
        assertThat(resource.getProtocol()).isEqualTo("file");
        Path sqlResource = Path.of(resource.toURI());
        Path hiddenResource = sqlResource.resolveSibling(
                sqlResource.getFileName() + ".coverage-hidden-" + UUID.randomUUID());
        Files.move(sqlResource, hiddenResource);

        try {
            assertThatThrownBy(SettlementStatementSql::fromClasspath)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Missing statement SQL resource: " + SettlementStatementSql.RESOURCE);
        } finally {
            Files.move(hiddenResource, sqlResource);
        }
    }

    @Test
    void rejectsAnUnresolvedNamedParameterBeforeJdbcExecution() {
        String template = """
                select :unownedParameter
                where true
                /*?from*/ and effective_at >= :from
                /*?to*/ and effective_at < :to
                /*?assignorId*/ and assignor_id = :assignorId
                /*?assetCurrency*/ and asset_currency_code = :assetCurrency
                /*?settlementCurrency*/ and settlement_currency_code = :settlementCurrency
                /*?productType*/ and product_type_code = :productType
                limit :limit offset :offset
                """;
        var renderer = SettlementStatementSql.fromTemplate(template);

        assertThatThrownBy(() -> renderer.render(
                        new SettlementStatementService.Filter(
                                null, null, null, null, null, null, 0, 50),
                        51,
                        0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unresolved statement SQL parameter: :unownedParameter");
    }

    @Test
    void rejectsTemplateMutationThatRemovesOrDuplicatesAuthorityMarkers() {
        String template = """
                select 1
                where true
                /*?from*/ and effective_at >= :from
                /*?to*/ and effective_at < :to
                /*?assignorId*/ and assignor_id = :assignorId
                /*?assetCurrency*/ and asset_currency_code = :assetCurrency
                /*?settlementCurrency*/ and settlement_currency_code = :settlementCurrency
                /*?productType*/ and product_type_code = :productType
                limit :limit offset :offset
                """;

        assertThatThrownBy(() -> SettlementStatementSql.fromTemplate(
                        template.replace("/*?productType*/ and product_type_code = :productType\n", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("filter markers");
        assertThatThrownBy(() -> SettlementStatementSql.fromTemplate(
                        template.replace(
                                "/*?productType*/ and product_type_code = :productType",
                                "/*?productType*/ and product_type_code = :productType\n"
                                        + "/*?productType*/ and product_type_code = :productType")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> SettlementStatementSql.fromTemplate(
                        template.replace(
                                "/*?settlementCurrency*/ and settlement_currency_code = :settlementCurrency\n"
                                        + "/*?productType*/ and product_type_code = :productType",
                                "/*?productType*/ and product_type_code = :productType\n"
                                        + "/*?settlementCurrency*/ and settlement_currency_code = :settlementCurrency")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("filter markers");
        assertThatThrownBy(() -> SettlementStatementSql.fromTemplate(
                        template.replace("limit :limit", "limit 51")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(":limit");
    }
}
