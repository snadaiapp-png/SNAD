# SANAD Stage 08 — SDK Strategy

**Document ID:** `SANAD-ST08-DEV-SDK-001`
**Track:** 8.8
**Date:** 2026-07-06

---

## 1. Languages (Initial)

* TypeScript / JavaScript (Node + browser).
* Python.
* Java.
* PHP.

Planned: Go, Ruby, C#.

---

## 2. Generation

* Auto-generated from OpenAPI spec.
* OpenAPI Generator toolchain.
* Hand-written wrappers for ergonomics (auth, retry, pagination).

---

## 3. Distribution

* npm (TypeScript).
* PyPI (Python).
* Maven Central (Java).
* Packagist (PHP).

---

## 4. Versioning

* SDK version independent of API version.
* SDK targets latest API version + N-1.
