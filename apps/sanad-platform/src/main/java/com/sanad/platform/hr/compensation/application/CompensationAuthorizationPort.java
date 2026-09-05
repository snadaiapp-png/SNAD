package com.sanad.platform.hr.compensation.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;

import java.util.UUID;

/**
 * Authorization port for compensation reads/writes (WS6 Task 3).
 * Independent capabilities — compensation visibility is NOT implied by
 * generic employee visibility:
 * <pre>
 *   HRM.COMPENSATION.MANAGE — create/revise/end packages
 *   HRM.COMPENSATION.VIEW   — component-amount reads (sensitive-read audited)
 * </pre>
 */
public interface CompensationAuthorizationPort {

    void requireManage(HrCommandContext ctx, UUID packageId);

    void requireView(HrCommandContext ctx, UUID employmentId);
}
