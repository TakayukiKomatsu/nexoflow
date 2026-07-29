package com.srm.creditengine.settlement.domain;

import java.util.UUID;

/** Receivable identity and version captured while its row lock is held. */
public record LockedReceivable(UUID id, long version) {
}
