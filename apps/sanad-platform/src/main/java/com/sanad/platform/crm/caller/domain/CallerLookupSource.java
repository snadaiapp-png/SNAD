package com.sanad.platform.crm.caller.domain;

/**
 * Bounded, extensible source contract for a caller lookup (G8-02 §24).
 *
 * <p>Adapters for {@code ANDROID_CALL}/{@code IOS_CALLER_EXTENSION}/{@code PBX}/
 * {@code VOIP} are NOT implemented in this track (G8 EXECUTION 02 keeps the
 * server-side core only; the enum is the forward contract).
 */
public enum CallerLookupSource {
    MANUAL,
    ANDROID_CALL,
    IOS_CALLER_EXTENSION,
    PBX,
    VOIP
}
