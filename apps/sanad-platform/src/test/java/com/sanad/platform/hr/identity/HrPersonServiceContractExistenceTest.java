package com.sanad.platform.hr.identity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WS2 Task 1B — Cycle 2 GREEN: Production Contract Existence.
 *
 * <p>This is the inverted form of the Cycle 1 RED test. After the Cycle 2
 * minimal production skeletons were added (HrPersonService, HrPersonRepository,
 * JdbcHrPersonRepository, IdentifierNormalizer, PersonIdentifier, HrPerson),
 * this test now asserts the production contracts <strong>do</strong> exist
 * and expose the planned method signatures.</p>
 *
 * <p>This file replaces the Cycle 1 reflection-based RED test that asserted
 * the classes were ABSENT. With the production skeletons now in place,
 * the absence assertion would fail — so the test is inverted to assert
 * PRESENCE and minimal signature compliance. This is the natural TDD
 * transition from Cycle 1 RED → Cycle 2 GREEN.</p>
 *
 * <p>The behavioral RED (Cycle 3) lives in a separate test file that
 * imports these production types and asserts real behavior.</p>
 */
class HrPersonServiceContractExistenceTest {

    private static final String[] PLANNED_PRODUCTION_TYPES = {
            "com.sanad.platform.hr.identity.HrPersonService",
            "com.sanad.platform.hr.identity.HrPersonRepository",
            "com.sanad.platform.hr.identity.JdbcHrPersonRepository",
            "com.sanad.platform.hr.identity.IdentifierNormalizer",
            "com.sanad.platform.hr.identity.PersonIdentifier",
            "com.sanad.platform.hr.identity.HrPerson"
    };

    @Test
    void plannedProductionContractsArePresent() {
        // GREEN state (Cycle 2): each planned production class MUST be loadable.
        for (String fqn : PLANNED_PRODUCTION_TYPES) {
            assertThat(noThrowClassForName(fqn))
                    .as("Production class %s must exist (GREEN state)", fqn)
                    .isTrue();
        }
    }

    @Test
    void hrPersonServiceExposesRequiredMethods() throws Exception {
        Class<?> svc = Class.forName("com.sanad.platform.hr.identity.HrPersonService");

        // createPerson(UUID, String, String, String)
        assertThat(svc.getMethod("createPerson", UUID.class, String.class, String.class, String.class))
                .as("HrPersonService.createPerson(UUID, String, String, String) must exist")
                .isNotNull();

        // linkUser(UUID, UUID, UUID)
        assertThat(svc.getMethod("linkUser", UUID.class, UUID.class, UUID.class))
                .as("HrPersonService.linkUser(UUID, UUID, UUID) must exist")
                .isNotNull();

        // addIdentifier(UUID, UUID, String, String, String)
        assertThat(svc.getMethod("addIdentifier", UUID.class, UUID.class,
                String.class, String.class, String.class))
                .as("HrPersonService.addIdentifier(UUID, UUID, String, String, String) must exist")
                .isNotNull();

        // findExactIdentifierMatch(UUID, String, String, String)
        assertThat(svc.getMethod("findExactIdentifierMatch", UUID.class,
                String.class, String.class, String.class))
                .as("HrPersonService.findExactIdentifierMatch(UUID, String, String, String) must exist")
                .isNotNull();
    }

    @Test
    void identifierNormalizerExposesRequiredMethods() throws Exception {
        Class<?> n = Class.forName("com.sanad.platform.hr.identity.IdentifierNormalizer");

        assertThat(n.getMethod("normalizeIdentifierType", String.class))
                .as("IdentifierNormalizer.normalizeIdentifierType(String) must exist")
                .isNotNull();

        assertThat(n.getMethod("normalizeCountryCode", String.class))
                .as("IdentifierNormalizer.normalizeCountryCode(String) must exist")
                .isNotNull();

        assertThat(n.getMethod("normalizeValue", String.class))
                .as("IdentifierNormalizer.normalizeValue(String) must exist")
                .isNotNull();
    }

    @Test
    void hrPersonRepositoryExposesRequiredMethods() throws Exception {
        Class<?> r = Class.forName("com.sanad.platform.hr.identity.HrPersonRepository");

        assertThat(r.getMethod("savePerson",
                Class.forName("com.sanad.platform.hr.identity.HrPerson")))
                .as("HrPersonRepository.savePerson(HrPerson) must exist")
                .isNotNull();

        assertThat(r.getMethod("findPersonById", UUID.class, UUID.class))
                .as("HrPersonRepository.findPersonById(UUID, UUID) must exist")
                .isNotNull();

        assertThat(r.getMethod("saveIdentifier",
                Class.forName("com.sanad.platform.hr.identity.PersonIdentifier")))
                .as("HrPersonRepository.saveIdentifier(PersonIdentifier) must exist")
                .isNotNull();

        assertThat(r.getMethod("findActiveIdentifierByBlindIndex",
                UUID.class, String.class, String.class, String.class))
                .as("HrPersonRepository.findActiveIdentifierByBlindIndex(UUID, String, String, String) must exist")
                .isNotNull();
    }

    @Test
    void jdbcHrPersonRepositoryImplementsRepository() throws Exception {
        Class<?> jdbc = Class.forName("com.sanad.platform.hr.identity.JdbcHrPersonRepository");
        Class<?> repo = Class.forName("com.sanad.platform.hr.identity.HrPersonRepository");

        assertThat(repo.isAssignableFrom(jdbc))
                .as("JdbcHrPersonRepository must implement HrPersonRepository")
                .isTrue();
    }

    private static boolean noThrowClassForName(String fqn) {
        try {
            Class.forName(fqn);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
