# Legacy Self-Hosted Backend Runtime

The legacy Windows NSSM and Linux systemd backend launch paths were retired from the active production tree during Clean-Room R7. Render image-backed deployment is the canonical backend runtime path.

## Why these paths were retired

The retired service definitions conflicted with the Clean-Room runtime contract because they:

- represented an independent self-hosted production runtime;
- enabled Flyway inside the web application process;
- bypassed the canonical PostgreSQL Direct migration authority;
- included fixed database authentication material in source.

The secret-bearing service definition files are intentionally **not copied into this archive**. Their Git history already preserves provenance; duplicating them would republish sensitive material in the current tree.

The non-secret installation/readme/quick-start helpers are retained here only as historical evidence and are no longer active production scripts.

No provider credential was rotated by this repository cleanup. Credential replacement remains a final project/cutover activity under the project owner's decision.
