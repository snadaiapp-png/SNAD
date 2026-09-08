package com.sanad.platform.hr.recruitment.domain;

/**
 * HRM-G1 — HrOffer lifecycle states (design §5.1/§6.3).
 *
 * <p>Edits after EXTENDED create a new OfferVersion and require re-extension
 * through approval — never in-place mutation of an extended offer.</p>
 */
public enum HrOfferState {
    DRAFT,
    PENDING_APPROVAL,
    EXTENDED,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    WITHDRAWN
}
