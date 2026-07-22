package com.srm.creditengine.pricing.infrastructure;

import com.srm.creditengine.pricing.application.ReceivableQuoteReader;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReceivableQuoteReader implements ReceivableQuoteReader {
    private final JdbcTemplate jdbc;

    public JdbcReceivableQuoteReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LockedReceivable> lockRegistered(UUID id) {
        return jdbc.query(
                        "select id,product_type_code,face_amount,face_currency_code,due_date,status "
                                + "from receivables where id=? for update",
                        (rs, row) -> new LockedReceivable(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getBigDecimal(3),
                                rs.getString(4),
                                rs.getDate(5).toLocalDate(),
                                rs.getString(6)),
                        id)
                .stream()
                .findFirst();
    }
}
