#!/usr/bin/env python3
from pathlib import Path

repo_path = Path('apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcCustomerMasterRepository.java')
repo = repo_path.read_text(encoding='utf-8')
start = repo.index('        int contacts = jdbc.update(', repo.index('public MergeResult mergeAccounts'))
end = repo.index('    private AccountAddress findAddress', start)
replacement = '''        params.addValue("sourceLegalName", source.legalName())
                .addValue("sourceTradingName", source.tradingName())
                .addValue("sourceRegistrationNumber", source.registrationNumber())
                .addValue("sourceTaxNumber", source.taxNumber())
                .addValue("sourceIndustryCode", source.industryCode())
                .addValue("sourceSegment", source.customerSegment())
                .addValue("sourceTier", source.customerTier())
                .addValue("sourceWebsite", source.website())
                .addValue("sourceEmail", source.primaryEmail())
                .addValue("sourcePhone", source.primaryPhone())
                .addValue("sourceCountry", source.countryCode())
                .addValue("sourceRisk", source.riskRating())
                .addValue("sourceCreditLimit", source.creditLimit())
                .addValue("sourcePaymentTerms", source.paymentTermsDays())
                .addValue("sourceQuality", source.dataQualityScore());
        int contacts = jdbc.update(
                "UPDATE crm_contacts SET account_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND account_id=:sourceId", params);
        int opportunities = jdbc.update(
                "UPDATE crm_opportunities SET account_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND account_id=:sourceId", params);
        int activities = jdbc.update(
                "UPDATE crm_activities SET related_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND related_type='ACCOUNT' AND related_id=:sourceId", params);
        int addresses = jdbc.update(
                "UPDATE crm_account_addresses SET account_id=:targetId,primary_address=FALSE,updated_by=:actorId," +
                        "updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND account_id=:sourceId", params);
        int identifiers = jdbc.update(
                "UPDATE crm_account_identifiers SET account_id=:targetId,primary_identifier=FALSE " +
                        "WHERE tenant_id=:tenantId AND account_id=:sourceId", params);
        jdbc.update(
                "DELETE FROM crm_account_relationships WHERE tenant_id=:tenantId AND " +
                        "((source_account_id=:sourceId AND target_account_id=:targetId) OR " +
                        "(source_account_id=:targetId AND target_account_id=:sourceId))", params);
        int relationships = jdbc.update(
                "UPDATE crm_account_relationships SET source_account_id=:targetId,updated_at=:now " +
                        "WHERE tenant_id=:tenantId AND source_account_id=:sourceId", params);
        relationships += jdbc.update(
                "UPDATE crm_account_relationships SET target_account_id=:targetId,updated_at=:now " +
                        "WHERE tenant_id=:tenantId AND target_account_id=:sourceId", params);
        jdbc.update(
                "UPDATE crm_accounts SET parent_account_id=:targetId,updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND parent_account_id=:sourceId AND id<>:targetId", params);
        int sourceUpdated = jdbc.update(
                "UPDATE crm_accounts SET lifecycle_status='ARCHIVED',archived_at=:now,merged_into_account_id=:targetId," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 WHERE tenant_id=:tenantId AND id=:sourceId " +
                        "AND version=:sourceVersion",
                params.addValue("sourceVersion", expectedSourceVersion));
        int targetUpdated = jdbc.update(
                "UPDATE crm_accounts SET legal_name=COALESCE(legal_name,:sourceLegalName)," +
                        "trading_name=COALESCE(trading_name,:sourceTradingName)," +
                        "registration_number=COALESCE(registration_number,:sourceRegistrationNumber)," +
                        "tax_number=COALESCE(tax_number,:sourceTaxNumber)," +
                        "industry_code=COALESCE(industry_code,:sourceIndustryCode)," +
                        "customer_segment=COALESCE(customer_segment,:sourceSegment)," +
                        "customer_tier=COALESCE(customer_tier,:sourceTier),website=COALESCE(website,:sourceWebsite)," +
                        "primary_email=COALESCE(primary_email,:sourceEmail),primary_phone=COALESCE(primary_phone,:sourcePhone)," +
                        "country_code=COALESCE(country_code,:sourceCountry),risk_rating=COALESCE(risk_rating,:sourceRisk)," +
                        "credit_limit=COALESCE(credit_limit,:sourceCreditLimit)," +
                        "payment_terms_days=COALESCE(payment_terms_days,:sourcePaymentTerms)," +
                        "data_quality_score=CASE WHEN data_quality_score>:sourceQuality THEN data_quality_score ELSE :sourceQuality END," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:targetId AND version=:targetVersion",
                params.addValue("targetVersion", expectedTargetVersion));
        if (sourceUpdated != 1 || targetUpdated != 1) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
        jdbc.update(
                "INSERT INTO crm_account_status_history (id,tenant_id,account_id,previous_status,new_status,reason," +
                        "changed_by,changed_at) VALUES (:id,:tenantId,:sourceId,:previousStatus,'ARCHIVED',:reason,:actorId,:now)",
                params.addValue("id", UUID.randomUUID()).addValue("previousStatus", source.lifecycleStatus())
                        .addValue("reason", reason));
        jdbc.update(
                "INSERT INTO crm_account_merge_history (id,tenant_id,source_account_id,target_account_id,source_version," +
                        "target_version,contacts_moved,opportunities_moved,activities_moved,addresses_moved," +
                        "identifiers_moved,relationships_moved,reason,merged_by,merged_at) " +
                        "VALUES (:mergeId,:tenantId,:sourceId,:targetId,:sourceVersion,:targetVersion,:contacts," +
                        ":opportunities,:activities,:addresses,:identifiers,:relationships,:reason,:actorId,:now)",
                params.addValue("mergeId", UUID.randomUUID()).addValue("contacts", contacts)
                        .addValue("opportunities", opportunities).addValue("activities", activities)
                        .addValue("addresses", addresses).addValue("identifiers", identifiers)
                        .addValue("relationships", relationships));
        return new MergeResult(sourceAccountId, targetAccountId, expectedSourceVersion + 1,
                expectedTargetVersion + 1, contacts, opportunities, activities,
                addresses, identifiers, relationships, now);
    }

'''
repo = repo[:start] + replacement + repo[end:]
repo_path.write_text(repo, encoding='utf-8')

use_path = Path('apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/CustomerMasterUseCases.java')
use = use_path.read_text(encoding='utf-8')
old = '''                .put("contactsMoved", result.contactsMoved())
                .put("opportunitiesMoved", result.opportunitiesMoved())
                .put("activitiesMoved", result.activitiesMoved());'''
new = '''                .put("contactsMoved", result.contactsMoved())
                .put("opportunitiesMoved", result.opportunitiesMoved())
                .put("activitiesMoved", result.activitiesMoved())
                .put("addressesMoved", result.addressesMoved())
                .put("identifiersMoved", result.identifiersMoved())
                .put("relationshipsMoved", result.relationshipsMoved());'''
if old not in use:
    raise SystemExit('CustomerMasterUseCases merge state marker not found')
use_path.write_text(use.replace(old, new, 1), encoding='utf-8')
