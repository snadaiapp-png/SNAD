package com.sanad.platform.subscription.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frontend ↔ backend SCP contract gate (incident prevention).
 *
 * Every URL template exported by {@code apps/web/lib/api/scp-api.ts} must be
 * owned by a Spring {@code /api/v1/executive} controller route with the same
 * HTTP verb. This test would have failed BEFORE the production incident in
 * which the SCP web client was deployed against a backend image that predated
 * the control-plane controllers (HTTP 404 on every executive surface).
 *
 * The backend side is read through Spring's own mapping annotations
 * (merged-annotation reflection on compiled controller classes — the same
 * metadata the MVC HandlerMapping consumes), not by grepping source strings,
 * so routes declared through arrays or base-path concatenation are resolved
 * exactly as Spring sees them.
 */
class ScpFrontendContractTest {

    private static final List<Class<?>> EXECUTIVE_CONTROLLERS = List.of(
            CatalogController.class,
            ExecutiveReadController.class,
            GovernanceController.class,
            LifecycleController.class,
            PlanVersionController.class,
            PriceController.class,
            SubscriptionItemController.class,
            com.sanad.platform.subscription.usage.UsageController.class,
            com.sanad.platform.executive.api.PlatformOperationsQueryController.class,
            com.sanad.platform.executive.api.PlatformOperationsCommandController.class,
            com.sanad.platform.executive.api.SaasAdministrationQueryController.class,
            com.sanad.platform.executive.api.SaasAdministrationCommandController.class,
            com.sanad.platform.module.api.ModuleRegistryController.class);

    private record Route(String verb, String pattern) {
    }

    private record Contract(String verb, String url) {
    }

    private static List<Contract> frontendContracts;
    private static Set<Route> backendRoutes;

    @BeforeAll
    static void loadContracts() throws IOException {
        frontendContracts = parseScpApiTemplates(locateScpApiSource());
        backendRoutes = collectBackendRoutes();
    }

    @Test
    void parserFindsTheFullFrontendSurface() {
        // Parser sanity: a silent 0-match regression must not pass vacuously.
        assertTrue(frontendContracts.size() >= 20,
                () -> "scp-api.ts contract parser found only " + frontendContracts.size()
                        + " operations — the extractor or the file layout changed");
    }

    @Test
    void backendExposesTheExecutiveRouteFamily() {
        assertTrue(backendRoutes.size() >= 60,
                () -> "unexpectedly few executive routes discovered: " + backendRoutes.size());
    }

    @Test
    void everyFrontendScpRouteIsOwnedByABackendMapping() {
        List<String> orphans = new ArrayList<>();
        for (Contract contract : frontendContracts) {
            boolean owned = backendRoutes.stream().anyMatch(route ->
                    route.verb().equals(contract.verb()) && matches(route.pattern(), contract.url()));
            if (!owned) {
                orphans.add(contract.verb() + " " + contract.url());
            }
        }
        assertTrue(orphans.isEmpty(),
                () -> "Frontend SCP routes without an owning backend endpoint "
                        + "(deploying this frontend against that backend artifact would 404): "
                        + orphans);
    }

    // ── frontend extraction ─────────────────────────────────────────────

    private static Path locateScpApiSource() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 8 && dir != null; depth++, dir = dir.getParent()) {
            Path candidate = dir.resolve("apps").resolve("web").resolve("lib")
                    .resolve("api").resolve("scp-api.ts");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError(
                "apps/web/lib/api/scp-api.ts not found — this contract test must run inside the monorepo");
    }

    /**
     * Extracts (urlTemplate, verb) pairs from the scpApi surface. The URL is
     * the first template literal of every {@code apiClient.<verb>(...)} call;
     * everything between the call and the literal (generics, type unions,
     * multi-line parameter declarations) is skipped without assuming a shape.
     * Interpolations are either {@code ${root}} (the shared
     * {@code /api/v1/executive} base) or path variables; {@code ${qs(...)}}
     * appends the query string and is irrelevant to routing.
     */
    private static List<Contract> parseScpApiTemplates(Path source) throws IOException {
        String text = Files.readString(source);
        Pattern call = Pattern.compile(
                "apiClient\\.(get|post|put|patch|delete)\\b[^`]*?`([^`]+)`");
        List<Contract> contracts = new ArrayList<>();
        Matcher matcher = call.matcher(text);
        while (matcher.find()) {
            String verb = matcher.group(1).toUpperCase();
            String template = normalizeTemplate(matcher.group(2));
            if (contracts.stream().noneMatch(existing ->
                    existing.verb().equals(verb) && existing.url().equals(template))) {
                contracts.add(new Contract(verb, template));
            }
        }
        return contracts;
    }

    private static String normalizeTemplate(String raw) {
        String value = raw
                .replace("${root}", "/api/v1/executive")
                .replaceAll("\\$\\{qs\\([^)]*\\)}", "")
                .replaceAll("\\$\\{([a-zA-Z]+)\\}", "{$1}");
        while (value.contains("//")) {
            value = value.replace("//", "/");
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    // ── backend extraction ──────────────────────────────────────────────

    private static Set<Route> collectBackendRoutes() {
        Set<Route> routes = new LinkedHashSet<>();
        for (Class<?> controller : EXECUTIVE_CONTROLLERS) {
            RequestMapping classMapping =
                    AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            if (classMapping == null || classMapping.value().length == 0) {
                throw new AssertionError(
                        controller.getSimpleName() + " must declare a class-level @RequestMapping");
            }
            String base = classMapping.value()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping =
                        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String verb = requestVerb(controller, method, mapping);
                String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
                if (paths.length == 0) {
                    routes.add(new Route(verb, base));
                } else {
                    for (String path : paths) {
                        routes.add(new Route(verb, (base + path).replaceAll("//+", "/")));
                    }
                }
            }
        }
        return routes;
    }

    private static String requestVerb(Class<?> controller, Method method, RequestMapping mapping) {
        RequestMethod[] methods = mapping.method();
        if (methods.length == 1) {
            return methods[0].name();
        }
        // @GetMapping-style meta-annotations always carry exactly one verb; a
        // bare method-level @RequestMapping would match every verb.
        throw new AssertionError(controller.getSimpleName() + "#" + method.getName()
                + " must declare a single-verb mapping (@GetMapping, @PostMapping, ...)");
    }

    // ── pattern matching ────────────────────────────────────────────────

    private static boolean matches(String pattern, String url) {
        String[] patternSegments = split(pattern);
        String[] urlSegments = split(url);
        if (patternSegments.length != urlSegments.length) {
            return false;
        }
        for (int i = 0; i < patternSegments.length; i++) {
            String p = patternSegments[i];
            String u = urlSegments[i];
            boolean patternVariable = p.startsWith("{") && p.endsWith("}");
            boolean urlVariable = u.startsWith("{") && u.endsWith("}");
            if (!patternVariable && !urlVariable && !p.equals(u)) {
                return false;
            }
        }
        return true;
    }

    private static String[] split(String value) {
        return Arrays.stream(value.split("/"))
                .filter(segment -> !segment.isEmpty())
                .toArray(String[]::new);
    }
}
