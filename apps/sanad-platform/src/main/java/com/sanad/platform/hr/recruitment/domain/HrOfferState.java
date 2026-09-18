package com.sanad.platform.hr.recruitment.domain;

/**
 * HRM-G1 — HrOffer lifecycle states (design §5.1/§6.3, restored by T7).
 *
 * <p>EXTENDED is reachable ONLY from PENDING_APPROVAL and only behind the
 * authoritative Workflow Y2 APPROVED outcome. Edits after EXTENDED create a
 * new OfferVersion and require re-extension through approval — never
 * in-place mutation of an extended offer.</p>
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
