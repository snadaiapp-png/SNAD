# SNAD Design System Changelog

## 2026-09-24 — Authentication official wordmark adoption

- Scope: authentication surfaces only.
- Approved source: `snad-logo-official-wordmark.png`, PNG RGBA `1162×337`.
- SHA-256: `98d0b84b0675b53f60837f803cf0a4bc85209eea650b213adba92a661bfde251`.
- Rendering owner: `apps/web/components/sds/SnadLogo.tsx`, variant `official-wordmark`.
- No color/token change.
- No generated SVG, white, dark, monochrome, compact, favicon, or app-icon derivative.
- Existing non-auth logo variants remain unchanged pending a separate global migration decision.
- Login v2 also hardens nested `/executive/**` post-login destination normalization without making the browser authoritative for authorization.

This record does not certify release readiness. Release status is determined by the exact-head Login v2 evidence gate and CI/security results.
