package com.srm.creditengine.receivable.application;

import java.util.UUID;

/**
 * Application-layer port that allows the receivable module to query an assignor's active status
 * without depending on the assignor's infrastructure layer.
 */
public interface AssignorStatusReader {

    /**
     * Returns {@code true} if the assignor with the given {@code assignorId} exists and is active.
     *
     * @throws IllegalArgumentException if no assignor with that id exists
     */
    boolean isActive(UUID assignorId);
}
