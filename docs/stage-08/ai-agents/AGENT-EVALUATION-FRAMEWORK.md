# SANAD Stage 08 — Agent Evaluation Framework

**Document ID:** `SANAD-ST08-AI-EVAL-001`
**Track:** 8.5
**Date:** 2026-07-06

---

## 1. Evaluation Datasets

* Per-agent evaluation set (≥ 50 cases).
* Arabic and English cases.
* Include adversarial (prompt injection) cases.
* Include hallucination trap cases.

---

## 2. Metrics

| Metric                  | Target      |
|-------------------------|-------------|
| Task success rate       | > 90%       |
| Hallucination rate      | < 5%        |
| Citation accuracy       | > 95%       |
| Prompt injection block  | > 99%       |
| Latency p95             | < 5s        |
| Cost per execution      | < $0.05     |
| Arabic quality score    | > 4.0 / 5.0 |
| English quality score   | > 4.0 / 5.0 |

---

## 3. Regression Tests

* Run on every agent version bump.
* Run on every model version bump.
* Run on every prompt version bump.
* Block release if any regression > 5%.

---

## 4. Bias Review

* Quarterly bias audit.
* Disparate impact analysis across tenant demographics (where data available).
* Findings addressed in next sprint.
