package com.sanad.platform.config;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.boot.context.metrics.buffering.StartupTimeline.TimelineEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

/**
 * Captures coarse startup lifecycle event timestamps and dumps the top-N
 * slowest Spring Boot startup steps on {@link ApplicationReadyEvent}.
 *
 * <p>This listener is registered programmatically via
 * {@link org.springframework.boot.SpringApplication#addListeners} so it can
 * observe early lifecycle events ({@link ApplicationStartingEvent},
 * {@link ApplicationEnvironmentPreparedEvent}) that fire BEFORE the
 * {@link org.springframework.context.ApplicationContext} exists — at which
 * point a {@code @Component @EventListener} would not yet be registered.
 * The same instance also receives {@link ApplicationPreparedEvent},
 * {@link ApplicationStartedEvent}, and {@link ApplicationReadyEvent}.</p>
 *
 * <p>The output is structured for forensic cold-start analysis:
 * <ul>
 *   <li>Five lifecycle event timestamps (starting / env prepared / prepared /
 *       started / ready) — gives phase boundaries to compare against BFF
 *       login timing.</li>
 *   <li>Top-30 slowest startup steps, sorted by self-duration (wall time of
 *       the step minus the wall time of its direct children). Each entry
 *       shows the step name, total duration, self duration, and child count.
 *       This is the same data shown by the {@code /actuator/startup} endpoint
 *       but written to logs so it is captured on Render Free cold starts
 *       without exposing additional actuator endpoints.</li>
 *   <li>A category-level summary aggregating durations by step name prefix
 *       (e.g. {@code spring.beans.instantiate}, {@code flyway.*}, etc.) so
 *       the biggest contributor classes are visible at a glance.</li>
 * </ul>
 * </p>
 *
 * <p>Overhead is bounded:
 * <ul>
 *   <li>Five event callbacks — microseconds each.</li>
 *   <li>One pass over the {@link BufferingApplicationStartup#getBufferedTimeline()}
 *       snapshot on ready — typically 5-30ms for ~5k events.</li>
 * </ul>
 * </p>
 */
public class StartupTimelineLogger implements ApplicationListener<ApplicationEvent> {

    private static final Logger log = LoggerFactory.getLogger("SANAD-STARTUP");

    private static final int TOP_N = 30;

    // Capture wall-clock timestamps for each lifecycle phase. Use Instant
    // rather than System.nanoTime() because the data is meant for cross-system
    // correlation (BFF logs, Render events, Vercel timestamps).
    private volatile Instant t0Starting;
    private volatile Instant t1EnvPrepared;
    private volatile Instant t2Prepared;
    private volatile Instant t3Started;
    private volatile Instant t4Ready;

    /** Captured on ApplicationReadyEvent — needed to look up ApplicationStartup. */
    private volatile ApplicationStartup applicationStartup;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // Use instanceof rather than a switch on class name so that subclasses
        // of the standard events also match.
        if (event instanceof ApplicationStartingEvent) {
            onStarting((ApplicationStartingEvent) event);
        } else if (event instanceof ApplicationEnvironmentPreparedEvent) {
            onEnvironmentPrepared((ApplicationEnvironmentPreparedEvent) event);
        } else if (event instanceof ApplicationPreparedEvent) {
            onPrepared((ApplicationPreparedEvent) event);
        } else if (event instanceof ApplicationStartedEvent) {
            onStarted((ApplicationStartedEvent) event);
        } else if (event instanceof ApplicationReadyEvent) {
            onReady((ApplicationReadyEvent) event);
        }
    }

    private void onStarting(ApplicationStartingEvent event) {
        t0Starting = Instant.now();
        log.info("[STARTUP] PHASE_0_APPLICATION_STARTING  ts={}  thread={}",
                t0Starting, Thread.currentThread().getName());
    }

    private void onEnvironmentPrepared(ApplicationEnvironmentPreparedEvent event) {
        t1EnvPrepared = Instant.now();
        long elapsed = t0Starting == null ? -1 : Duration.between(t0Starting, t1EnvPrepared).toMillis();
        log.info("[STARTUP] PHASE_1_ENVIRONMENT_PREPARED  ts={}  elapsed_from_start={}ms",
                t1EnvPrepared, elapsed);
    }

    private void onPrepared(ApplicationPreparedEvent event) {
        t2Prepared = Instant.now();
        long elapsed = t0Starting == null ? -1 : Duration.between(t0Starting, t2Prepared).toMillis();
        log.info("[STARTUP] PHASE_2_APPLICATION_PREPARED  ts={}  elapsed_from_start={}ms",
                t2Prepared, elapsed);
    }

    private void onStarted(ApplicationStartedEvent event) {
        t3Started = Instant.now();
        long elapsed = t0Starting == null ? -1 : Duration.between(t0Starting, t3Started).toMillis();
        log.info("[STARTUP] PHASE_3_APPLICATION_STARTED   ts={}  elapsed_from_start={}ms",
                t3Started, elapsed);
    }

    private void onReady(ApplicationReadyEvent event) {
        t4Ready = Instant.now();
        long totalMs = t0Starting == null ? -1 : Duration.between(t0Starting, t4Ready).toMillis();
        log.info("[STARTUP] PHASE_4_APPLICATION_READY     ts={}  total_startup_ms={}",
                t4Ready, totalMs);

        // Phase summary block — easy to grep from logs.
        if (t0Starting != null && t1EnvPrepared != null && t2Prepared != null
                && t3Started != null && t4Ready != null) {
            long p0to1 = Duration.between(t0Starting, t1EnvPrepared).toMillis();
            long p1to2 = Duration.between(t1EnvPrepared, t2Prepared).toMillis();
            long p2to3 = Duration.between(t2Prepared, t3Started).toMillis();
            long p3to4 = Duration.between(t3Started, t4Ready).toMillis();
            log.info("[STARTUP] SUMMARY phase_0_to_1_env_ms={}  phase_1_to_2_ctx_init_ms={}  "
                    + "phase_2_to_3_bean_instantiation_ms={}  phase_3_to_4_runner_ms={}  total_ms={}",
                    p0to1, p1to2, p2to3, p3to4, totalMs);
        }

        // Capture the ApplicationStartup reference and dump top-N slowest
        // startup steps. Note: use getBufferedTimeline() (non-draining) so
        // the buffer remains available for a later actuator request.
        applicationStartup = event.getSpringApplication().getApplicationStartup();
        try {
            dumpStartupSteps(applicationStartup);
        } catch (Exception e) {
            log.warn("[STARTUP] Failed to dump startup steps (non-fatal): {}", e.toString());
        }
    }

    private void dumpStartupSteps(ApplicationStartup applicationStartup) {
        if (!(applicationStartup instanceof BufferingApplicationStartup buffering)) {
            log.warn("[STARTUP] BufferingApplicationStartup not active — skipping step dump. "
                    + "Actual type: {}", applicationStartup == null ? "null" : applicationStartup.getClass().getName());
            return;
        }

        StartupTimeline timeline = buffering.getBufferedTimeline();
        if (timeline == null) {
            log.warn("[STARTUP] BufferingApplicationStartup timeline null.");
            return;
        }

        List<TimelineEvent> events = timeline.getEvents();
        if (events == null || events.isEmpty()) {
            log.warn("[STARTUP] BufferingApplicationStartup timeline empty — no events recorded.");
            return;
        }

        log.info("[STARTUP] TIMELINE  total_events={}  timeline_start={}",
                events.size(), timeline.getStartTime());

        // Build maps for self-duration calculation. The StartupStep.getId()
        // returns Long (nullable). For events where id is null we skip them
        // (we cannot correlate to a parent).
        Map<Long, Long> idToTotalNanos = new HashMap<>(events.size() * 2);
        Map<Long, Long> idToParentId = new HashMap<>(events.size() * 2);
        Map<Long, String> idToName = new HashMap<>(events.size() * 2);
        Map<Long, Integer> idToChildCount = new HashMap<>(events.size() * 2);
        Map<Long, Long> idToChildrenTotalNanos = new HashMap<>(events.size() * 2);

        for (TimelineEvent ev : events) {
            StartupStep step = ev.getStartupStep();
            if (step == null) continue;
            Long id = step.getId();
            if (id == null) continue;
            Duration total = ev.getDuration();
            long totalNanos = total == null ? 0L : total.toNanos();
            idToTotalNanos.put(id, totalNanos);
            idToParentId.put(id, step.getParentId());
            idToName.put(id, step.getName() != null ? step.getName() : "<unnamed>");
            idToChildCount.putIfAbsent(id, 0);
            idToChildrenTotalNanos.putIfAbsent(id, 0L);
            // Increment parent's child count and add this step's total to
            // parent's children total.
            Long parentId = step.getParentId();
            if (parentId != null) {
                idToChildCount.merge(parentId, 1, Integer::sum);
                idToChildrenTotalNanos.merge(parentId, totalNanos, Long::sum);
            }
        }

        // Build step entries with self-duration = total - sum(child totals).
        List<StepEntry> entries = new ArrayList<>(events.size());
        for (Long id : idToTotalNanos.keySet()) {
            long total = idToTotalNanos.getOrDefault(id, 0L);
            long childrenTotal = idToChildrenTotalNanos.getOrDefault(id, 0L);
            long self = Math.max(0L, total - childrenTotal);
            String name = idToName.getOrDefault(id, "<unnamed>");
            int childCount = idToChildCount.getOrDefault(id, 0);
            entries.add(new StepEntry(name, id, total, self, childCount));
        }

        // Sort by self duration descending.
        entries.sort(Comparator.comparingLong((StepEntry e) -> -e.selfDurationNanos)
                .thenComparing(e -> e.name));

        log.info("[STARTUP] TOP_{}_SLOWEST_STEPS_BY_SELF_DURATION total_steps_recorded={}",
                TOP_N, entries.size());
        int rank = 1;
        for (StepEntry e : entries.subList(0, Math.min(TOP_N, entries.size()))) {
            log.info("[STARTUP] RANK_{}  name={}  total_ms={}  self_ms={}  children={}",
                    String.format("%02d", rank),
                    e.name,
                    e.totalDurationNanos / 1_000_000,
                    e.selfDurationNanos / 1_000_000,
                    e.childCount);
            rank++;
        }

        // Category aggregation — group by first 2 dot segments of the name.
        Map<String, long[]> byCategory = new HashMap<>();
        for (StepEntry e : entries) {
            String cat = categoryOf(e.name);
            long[] agg = byCategory.computeIfAbsent(cat, k -> new long[2]);
            agg[0] += e.totalDurationNanos;
            agg[1] += 1;
        }
        List<Map.Entry<String, long[]>> sortedCats = new ArrayList<>(byCategory.entrySet());
        sortedCats.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));

        log.info("[STARTUP] CATEGORY_SUMMARY categories={}", sortedCats.size());
        for (Map.Entry<String, long[]> entry : sortedCats) {
            log.info("[STARTUP] CATEGORY name={}  total_ms={}  step_count={}",
                    entry.getKey(),
                    entry.getValue()[0] / 1_000_000,
                    entry.getValue()[1]);
        }
    }

    /** Extract a 2-segment category from a step name like "spring.beans.instantiate". */
    private static String categoryOf(String name) {
        if (name == null || name.isEmpty()) {
            return "<empty>";
        }
        int first = name.indexOf('.');
        if (first < 0) {
            return name;
        }
        int second = name.indexOf('.', first + 1);
        return second < 0 ? name.substring(0, first + 1) + "*" : name.substring(0, second);
    }

    /** Lightweight value class for startup-step dump output. */
    private static final class StepEntry {
        final String name;
        final long id;
        final long totalDurationNanos;
        final long selfDurationNanos;
        final int childCount;

        StepEntry(String name, long id, long totalDurationNanos, long selfDurationNanos, int childCount) {
            this.name = name;
            this.id = id;
            this.totalDurationNanos = totalDurationNanos;
            this.selfDurationNanos = selfDurationNanos;
            this.childCount = childCount;
        }
    }
}
