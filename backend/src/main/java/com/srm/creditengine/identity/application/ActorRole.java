package com.srm.creditengine.identity.application;

/** Roles are deliberately closed: an unknown database value never grants access. */
public enum ActorRole {
    OPERATOR, ANALYST, ADMIN, AUDITOR
}
