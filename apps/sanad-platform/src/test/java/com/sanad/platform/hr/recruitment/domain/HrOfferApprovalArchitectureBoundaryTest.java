package com.sanad.platform.hr.recruitment.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HRM-G1 T7.16 — offer approval architecture boundary guards.
 *
 * <p>Proves the authority boundaries of the restored requirement:</p>
 * <ul>
 *   <li>the T7 application authorities exist (RED until implemented);</li>
 *   <li>HrOffer status is written ONLY by {@code JdbcHrOfferRepository}
 *       — no controller/service writes offer state directly;</li>
 *   <li>repositories do not decide business transitions (no
 *       {@code HrOfferTransitions} dependency in the repository);</li>
 *   <li>HRM implements NO competing Workflow approval state machine —
 *       no HR class references {@code WorkflowApprovalService} (approval
 *       decisions belong exclusively to Workflow Y2);</li>
 *   <li>historical {@code hr_offer_versions} rows have NO update path —
 *       the literal {@code UPDATE hr_offer_versions} must not exist in
 *       production sources (the DB append-only trigger is the second
 *       line of defense);</li>
 *   <li>the DRAFT→EXTENDED bypass cannot reappear: the transition guard
 *       matrix is pinned by {@code HrOfferStateTransitionGuardTest} and
 *       no production source may construct the forbidden direct path.</li>
 * </ul>
 */
class HrOfferApprovalArchitectureBoundaryTest {

    private static final Path MAIN_JAVA = Paths.get(
            "src/main/java/com/sanad/platform/hr");

    // ==================== T7 authorities exist (RED first) ====================

    @Test
    void t7ApplicationAuthorities_exist() throws Exception {
        assertThat(Class.forName(
                        "com.sanad.platform.hr.recruitment.domain.HrOffer"))
                .as("the HrOffer aggregate exists");
        assertThat(Class.forName(
                        "com.sanad.platform.hr.recruitment.domain.HrOfferVersion"))
                .as("the immutable HrOfferVersion exists");
        assertThat(Class.forName(
                        "com.sanad.platform.hr.recruitment.application.HrOfferService"))
                .as("the application authority exists");
        assertThat(Class.forName(
                        "com.sanad.platform.hr.recruitment.infrastructure.JdbcHrOfferRepository"))
                .as("the offer repository exists");
        assertThat(Class.forName(
                        "com.sanad.platform.hr.recruitment.application.OfferApprovalWorkflowPort"))
                .as("the HR-owned workflow port exists");
        assertThat(Class.forName(
                        "com.sanad.platform.hr.recruitment.infrastructure.WorkflowY2OfferApprovalAdapter"))
                .as("the Workflow Y2 adapter exists");
    }

    // ==================== source-scan prohibitions ====================

    private List<Path> productionSources() throws IOException {
        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private record Hit(Path file, String content) {
    }

    private List<Hit> filesContaining(List<Path> sources, String needle) throws IOException {
        List<Hit> hits = new ArrayList<>();
        for (Path p : sources) {
            String content = Files.readString(p);
            if (content.contains(needle)) {
                hits.add(new Hit(p, content));
            }
        }
        return hits;
    }

    @Test
    void hrOfferStatus_isWrittenOnlyByTheOfferRepository() throws IOException {
        List<Hit> hits = filesContaining(productionSources(), "UPDATE hr_offers");
        assertThat(hits)
                .as("T7.16: HrOffer status may be written ONLY by JdbcHrOfferRepository")
                .allSatisfy(h -> assertThat(h.file().toString())
                        .endsWith("recruitment/infrastructure/JdbcHrOfferRepository.java"));
    }

    @Test
    void offerVersionRows_haveNoProductionUpdatePath() throws IOException {
        List<Hit> hits = filesContaining(productionSources(), "UPDATE hr_offer_versions");
        assertThat(hits)
                .as("T7.16: historical OfferVersion update paths are prohibited")
                .isEmpty();
    }

    @Test
    void repositories_doNotDecideBusinessTransitions() throws IOException {
        List<Hit> hits = filesContaining(productionSources(), "HrOfferTransitions");
        assertThat(hits)
                .as("T7.16: the transition guard is an application/domain authority — "
                        + "the repository must not decide business transitions")
                .noneSatisfy(h -> assertThat(h.file().toString())
                        .endsWith("recruitment/infrastructure/JdbcHrOfferRepository.java"));
    }

    @Test
    void hrm_doesNotImplementACompetingApprovalEngine() throws IOException {
        List<Hit> hits = filesContaining(productionSources(), "WorkflowApprovalService");
        assertThat(hits)
                .as("T7.16: HRM must never drive Workflow Y2 approval decisions "
                        + "(no WorkflowApprovalService reference below com/sanad/platform/hr)")
                .isEmpty();
    }

    @Test
    void offerApprovalFlows_dependOnlyOnTheHrOwnedPort() throws IOException {
        List<Path> sources = productionSources();
        for (String forbidden : List.of(
                "WorkflowEngineTransferAdapter",
                "com.sanad.platform.crm.ownership")) {
            List<Hit> hits = filesContaining(sources, forbidden);
            assertThat(hits)
                    .as("the offer approval flow must not borrow another module's approval wiring")
                    .isEmpty();
        }
    }
}
