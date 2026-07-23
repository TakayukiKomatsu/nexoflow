package com.srm.creditengine.assignor.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.srm.creditengine.assignor.domain.Assignor;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcAssignorRepositoryTest {

    private static final UUID ASSIGNOR_ID = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final Instant CREATED_AT = Instant.parse("2026-07-22T12:00:00Z");
    private static final String CREATED_BY = "operator@srm.local";

    @Test
    void findByIdRestoresThePersistedCreator() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = assignorRow();
        when(jdbc.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.<RowMapper<Assignor>>any(),
                        org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenAnswer(invocation -> map(invocation.getArgument(1), row));

        Assignor loaded = new JdbcAssignorRepository(jdbc).findById(ASSIGNOR_ID).orElseThrow();

        assertThat(loaded.createdBy()).isEqualTo(CREATED_BY);
    }

    @Test
    void findAllRestoresThePersistedCreator() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet row = assignorRow();
        when(jdbc.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.<RowMapper<Assignor>>any()))
                .thenAnswer(invocation -> map(invocation.getArgument(1), row));

        List<Assignor> loaded = new JdbcAssignorRepository(jdbc).findAll();

        assertThat(loaded).singleElement().extracting(Assignor::createdBy).isEqualTo(CREATED_BY);
    }

    private static ResultSet assignorRow() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getObject(1, UUID.class)).thenReturn(ASSIGNOR_ID);
        when(row.getString(2)).thenReturn("Senior Receivables Ltda");
        when(row.getString(3)).thenReturn("12345678000195");
        when(row.getBoolean(4)).thenReturn(true);
        when(row.getTimestamp(5)).thenReturn(Timestamp.from(CREATED_AT));
        when(row.getString(6)).thenReturn(CREATED_BY);
        return row;
    }

    private static List<Assignor> map(RowMapper<Assignor> mapper, ResultSet row) throws Exception {
        return List.of(mapper.mapRow(row, 0));
    }
}
