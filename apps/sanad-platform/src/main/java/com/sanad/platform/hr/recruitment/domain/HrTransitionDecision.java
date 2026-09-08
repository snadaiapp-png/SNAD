package com.sanad.platform.hr.recruitment.domain;

/**
 * HRM-G1 — outcome of a domain transition guard check (design §6 fail-closed).
 *
 * <p>Guards NEVER throw for policy rejections; they return a decision with a
 * stable violation code the service layer maps to §15 error codes.</p>
 */
public record HrTransitionDecision(boolean allowed, String violationCode) {

    public static HrTransitionDecision allow() {
        return new HrTransitionDecision(true, null);
    }

    public static HrTransitionDecision forbid(String violationCode) {
        return new HrTransitionDecision(false, violationCode);
    }
}
