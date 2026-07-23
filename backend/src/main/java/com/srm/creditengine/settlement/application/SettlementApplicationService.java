package com.srm.creditengine.settlement.application;

import com.srm.creditengine.settlement.domain.SettlementDraft;
import com.srm.creditengine.settlement.domain.SettlementPolicy;
import com.srm.creditengine.settlement.domain.SettlementPreview;
import com.srm.creditengine.settlement.domain.LockedQuote;
import com.srm.creditengine.shared.domain.DomainResourceNotFoundException;
import com.srm.creditengine.shared.runtime.FinancialTelemetry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementApplicationService implements SettlementService {
    private static final String OPERATION = "SETTLEMENT_CREATE";
    private static final String REVERSAL_OPERATION = "SETTLEMENT_REVERSE";

    private final SettlementRepository settlements;
    private final IdempotencyRepository idempotency;
    private final AuditEventRecorder auditEvents;
    private final Clock clock;
    private final FinancialTelemetry telemetry;

    public SettlementApplicationService(
            SettlementRepository settlements,
            IdempotencyRepository idempotency,
            AuditEventRecorder auditEvents,
            Clock clock,
            FinancialTelemetry telemetry) {
        this.settlements = settlements;
        this.idempotency = idempotency;
        this.auditEvents = auditEvents;
        this.clock = clock;
        this.telemetry = telemetry;
    }

    @Override
    public Preview preview(List<UUID> orderedQuoteIds, String actor) {
        SettlementPolicy.requireOrderedUnique(orderedQuoteIds);
        Instant now = clock.instant();
        return toPreview(SettlementPolicy.previewOf(validatedQuotes(orderedQuoteIds, now), now));
    }

    @Override
    @Transactional
    public Result settle(List<UUID> orderedQuoteIds, String idempotencyKey, String actor) {
        String requestHash = SettlementPolicy.requestHash(orderedQuoteIds);
        var claim = idempotency.claim(actor, OPERATION, idempotencyKey, requestHash, clock.instant());
        if (!claim.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        if (claim.completed()) {
            return replay(settlements.findResult(claim.settlementId()).orElseThrow());
        }

        Instant now = clock.instant();
        var quotes = validatedQuotes(orderedQuoteIds, now);
        Preview preview = toPreview(SettlementPolicy.previewOf(quotes, now));
        var draft = new SettlementDraft(
                UUID.randomUUID(), quotes.getFirst().assignorId(), preview.settlementCurrency(), preview.totalAmount(), quotes, now, actor);
        settlements.saveCompleted(draft);
        for (int index = 0; index < quotes.size(); index++) {
            var quote = quotes.get(index);
            settlements.consumeQuoteAndReceivable(quote);
            settlements.saveItem(draft, quote, index + 1);
        }
        idempotency.completeSettlement(claim.id(), draft.settlementId(), now);
        auditEvents.record(actor, "SETTLEMENT_CREATED", "SETTLEMENT", draft.settlementId(), now, "{\"itemCount\":" + quotes.size() + "}");
        telemetry.settlement(preview.settlementCurrency(), "success");
        return settlements.findResult(draft.settlementId()).orElseThrow();
    }

    @Override
    public Result get(UUID settlementId) {
        if (settlementId == null) {
            throw new IllegalArgumentException("Settlement ID is required");
        }
        return settlements.findResult(settlementId).orElseThrow(DomainResourceNotFoundException::new);
    }

    @Override
    @Transactional
    public Reversal reverse(UUID settlementId, String reason, String idempotencyKey, String actor) {
        String requestHash = SettlementPolicy.reversalRequestHash(settlementId, reason);
        var claim = idempotency.claim(actor, REVERSAL_OPERATION, idempotencyKey, requestHash, clock.instant());
        if (!claim.requestHash().equals(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        if (claim.completed()) {
            return replay(settlements.findReversal(claim.reversalId()).orElseThrow());
        }

        Instant now = clock.instant();
        var reversal = settlements.reverse(settlements.lockSettlement(settlementId), reason.trim(), now, actor);
        idempotency.completeReversal(claim.id(), reversal.reversalId(), now);
        auditEvents.record(actor, "SETTLEMENT_REVERSED", "SETTLEMENT_REVERSAL", reversal.reversalId(), now,
                "{\"settlementId\":\"" + reversal.settlementId() + "\"}");
        telemetry.reversal("success");
        return reversal;
    }

    private static Result replay(Result result) {
        return new Result(result.settlementId(), result.status(), result.items(), result.settlementCurrency(),
                result.totalAmount(), result.completedAt(), true);
    }

    private static Reversal replay(Reversal reversal) {
        return new Reversal(reversal.reversalId(), reversal.settlementId(), reversal.reason(), reversal.reversedAt(), true);
    }

    private List<LockedQuote> validatedQuotes(List<UUID> orderedQuoteIds, Instant now) {
        List<LockedQuote> locked = settlements.lockQuotes(orderedQuoteIds);
        if (locked.size() != orderedQuoteIds.size()) {
            throw new DomainResourceNotFoundException();
        }
        return SettlementPolicy.validateQuotes(orderedQuoteIds, locked, now);
    }

    private static Preview toPreview(SettlementPreview preview) {
        return new Preview(
                preview.items().stream()
                        .map(item -> new Item(item.quoteId(), item.receivableId(), item.settlementAmount()))
                        .toList(),
                preview.settlementCurrency(),
                preview.totalAmount(),
                preview.asOf(),
                preview.earliestExpiry());
    }
}
