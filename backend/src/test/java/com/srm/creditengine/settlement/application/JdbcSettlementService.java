package com.srm.creditengine.settlement.infrastructure;

import com.srm.creditengine.settlement.application.SettlementApplicationService;
import com.srm.creditengine.settlement.application.SettlementService;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Test fixture retaining the former constructor while exercising the extracted production layers. */
public final class JdbcSettlementService implements SettlementService {
    private final SettlementApplicationService delegate;

    public JdbcSettlementService(org.springframework.jdbc.core.JdbcTemplate jdbc, Clock clock, FinancialTelemetry telemetry) {
        delegate = new SettlementApplicationService(
                new JdbcSettlementRepository(jdbc),
                new JdbcIdempotencyRepository(jdbc),
                new JdbcAuditEventRecorder(jdbc),
                clock,
                telemetry);
    }

    @Override public Preview preview(List<UUID> ids, String actor) { return delegate.preview(ids, actor); }
    @Override public Result settle(List<UUID> ids, String key, String actor) { return delegate.settle(ids, key, actor); }
    @Override public Result get(UUID id) { return delegate.get(id); }
    @Override public Reversal reverse(UUID id, String reason, String key, String actor) { return delegate.reverse(id, reason, key, actor); }
}
