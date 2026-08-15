package com.sanad.platform.management.health;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Auto-discovery registry of all {@link SystemHealthContributor} beans (v20260816.1).
 *
 * <p>Spring injects a {@code List<SystemHealthContributor>} automatically — every
 * {@code @Component} that implements the contract is collected here. This means:
 *
 * <ul>
 *   <li>Adding a new health contributor (e.g. ERP) requires only one new
 *       {@code @Component} class implementing {@link SystemHealthContributor}.</li>
 *   <li>The Central Health core ({@link SystemHealthAggregationService}) needs
 *       NO modification when a new contributor is added.</li>
 *   <li>Unknown / future modules are gracefully tolerated.</li>
 * </ul>
 */
@Component
public class SystemHealthContributorRegistry {

    private final List<SystemHealthContributor> contributors;

    public SystemHealthContributorRegistry(List<SystemHealthContributor> contributors) {
        this.contributors = contributors != null ? contributors : List.of();
    }

    /**
     * List all registered contributors.
     * Spring autowires the full list at construction time.
     */
    public List<SystemHealthContributor> allContributors() {
        return contributors;
    }

    /**
     * List all contributor IDs (for diagnostics and governance health).
     */
    public List<String> allContributorIds() {
        return contributors.stream()
                .map(SystemHealthContributor::componentId)
                .sorted()
                .toList();
    }

    /**
     * Find a specific contributor by ID. Returns empty if not registered.
     */
    public Optional<SystemHealthContributor> find(String componentId) {
        if (componentId == null) return Optional.empty();
        return contributors.stream()
                .filter(c -> componentId.equals(c.componentId()))
                .findFirst();
    }

    /**
     * Return contributors sorted by componentType then componentId for stable UI ordering.
     */
    public List<SystemHealthContributor> sortedContributors() {
        List<SystemHealthContributor> sorted = new ArrayList<>(contributors);
        sorted.sort(Comparator
                .comparing(SystemHealthContributor::componentType)
                .thenComparing(SystemHealthContributor::componentId));
        return sorted;
    }
}
