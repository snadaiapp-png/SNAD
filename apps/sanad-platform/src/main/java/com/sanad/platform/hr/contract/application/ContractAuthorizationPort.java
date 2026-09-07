package com.sanad.platform.hr.contract.application;

import com.sanad.platform.hr.compliance.domain.HrCommandContext;

import java.util.UUID;

/**
 * Authorization port for contract commands/reads (WS6 Task 2).
 *
 * <p>Keeps the contract service decoupled from the concrete scoped
 * authorization implementation (port/adapter, mirroring
 * {@code ComplianceOverrideAuthorizationPort}). The production adapter binds
 * the independent contract capabilities:</p>
 * <pre>
 *   HRM.CONTRACT.MANAGE — create/amend/activate/terminate
 *   HRM.CONTRACT.VIEW   — protected contract reads
 * </pre>
 */
public interface ContractAuthorizationPort {

    /** Throws when the actor lacks HRM.CONTRACT.MANAGE on the resource scope. */
    void requireManage(HrCommandContext ctx, UUID contractId);

    /** Throws when the actor lacks HRM.CONTRACT.VIEW on the resource scope. */
    void requireView(HrCommandContext ctx, UUID contractId);
}
