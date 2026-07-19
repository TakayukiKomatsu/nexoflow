package com.srm.creditengine.identity.application;

/** Application seam for the authenticated actor; no caller needs to parse a JWT. */
public interface ActorContext {
    CurrentActor currentActor();
}
