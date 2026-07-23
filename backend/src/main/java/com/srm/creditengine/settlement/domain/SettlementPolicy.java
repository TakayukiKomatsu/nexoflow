package com.srm.creditengine.settlement.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure settlement validation, aggregate calculation, and deterministic command hashing. */
public final class SettlementPolicy {
    public static final int MAX_QUOTE_IDS = 100;

    private SettlementPolicy() {
    }

    public static void requireOrderedUnique(List<UUID> quoteIds) {
        if (quoteIds == null || quoteIds.isEmpty()) {
            throw new IllegalArgumentException("At least one pricing quote is required");
        }
        if (quoteIds.size() > MAX_QUOTE_IDS) {
            throw new IllegalArgumentException("At most 100 pricing quotes may be settled together");
        }
        if (quoteIds.stream().anyMatch(Objects::isNull) || quoteIds.stream().distinct().count() != quoteIds.size()) {
            throw new IllegalArgumentException("Pricing quote IDs must be ordered and unique");
        }
    }

    public static List<LockedQuote> validateQuotes(List<UUID> orderedIds, List<LockedQuote> lockedQuotes, Instant now) {
        requireOrderedUnique(orderedIds);
        if (lockedQuotes.size() != orderedIds.size()) {
            throw new IllegalArgumentException("One or more pricing quotes were not found");
        }
        var byId = new HashMap<UUID, LockedQuote>();
        lockedQuotes.forEach(quote -> byId.put(quote.quoteId(), quote));
        var ordered = new ArrayList<LockedQuote>();
        for (UUID id : orderedIds) {
            LockedQuote quote = byId.get(id);
            if (quote == null) {
                throw new IllegalArgumentException("One or more pricing quotes were not found");
            }
            if ("CONSUMED".equals(quote.quoteStatus())) {
                throw new AlreadySettledException(quote.settlementCurrency());
            }
            if (!"ACTIVE".equals(quote.quoteStatus()) || !now.isBefore(quote.expiresAt())) {
                throw new PricingQuoteExpiredException();
            }
            if (!"REGISTERED".equals(quote.receivableStatus())) {
                throw new AlreadySettledException(quote.settlementCurrency());
            }
            ordered.add(quote);
        }
        if (ordered.stream().map(LockedQuote::receivableId).distinct().count() != ordered.size()) {
            throw new IllegalArgumentException("Pricing quotes must reference unique receivables");
        }
        LockedQuote first = ordered.getFirst();
        if (ordered.stream().anyMatch(quote -> !quote.assignorId().equals(first.assignorId())
                || !quote.settlementCurrency().equals(first.settlementCurrency()))) {
            throw new IllegalArgumentException("Pricing quotes must have one assignor and settlement currency");
        }
        return List.copyOf(ordered);
    }

    public static SettlementPreview previewOf(List<LockedQuote> quotes, Instant asOf) {
        BigDecimal total = quotes.stream()
                .map(LockedQuote::settlementAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_EVEN);
        int integerDigits = Math.max(0, total.precision() - total.scale());
        if (total.signum() <= 0 || integerDigits > 15) {
            throw new IllegalArgumentException(
                    "Settlement total must be positive and fit within 15 integer digits");
        }
        Instant earliest = quotes.stream().map(LockedQuote::expiresAt).min(Instant::compareTo).orElseThrow();
        return new SettlementPreview(
                quotes.stream().map(quote -> new SettlementPreview.Item(
                        quote.quoteId(), quote.receivableId(), quote.settlementAmount())).toList(),
                quotes.getFirst().settlementCurrency(), total, asOf, earliest);
    }

    public static String requestHash(List<UUID> quoteIds) {
        requireOrderedUnique(quoteIds);
        return sha256(String.join(",", quoteIds.stream().map(UUID::toString).toList()));
    }

    public static String reversalRequestHash(UUID settlementId, String reason) {
        validateReversalInput(settlementId, reason);
        return sha256(settlementId + "|" + reason.trim());
    }

    public static void validateReversalInput(UUID settlementId, String reason) {
        if (settlementId == null || reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("A reversal reason is required");
        }
    }

    private static String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
