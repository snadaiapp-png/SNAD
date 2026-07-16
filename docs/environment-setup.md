# Environment Setup Guide

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+ or the Maven wrapper
- Node.js 24.x
- PostgreSQL 16 for production-like local execution
- Docker when running Testcontainers suites
- ngrok for connecting the Vercel frontend to the backend running on Windows
- GitHub CLI and Vercel CLI for automatic remote configuration

## Backend — Local Windows Server

The deployed SANAD backend currently runs on the same Windows computer as the development workspace and listens on port `8080`.

```powershell
# Production-style local service
powershell -ExecutionPolicy Bypass -File scripts\windows\start-sanad-production.ps1

# Health verification
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Expected result:

```json
{"status":"UP"}
```

For source-based local development:

```powershell
cd apps\sanad-platform
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

## Frontend — Local Development

The browser uses the same-origin Next.js BFF at `/api/platform`. The BFF connects to the local backend server-side, so browser CORS is not required.

```powershell
Copy-Item apps\web\.env.local.example apps\web\.env.local
cd apps\web
npm install
npm run dev
```

Default local frontend URL:

```text
http://localhost:3000
```

Required local frontend variables:

```dotenv
BACKEND_API_BASE_URL=http://127.0.0.1:8080
BACKEND_REQUEST_TIMEOUT_MS=15000
```

`NEXT_PUBLIC_API_BASE_URL` is optional and should normally remain unset. Setting it causes direct browser-to-backend traffic and reintroduces CORS and cross-site authentication constraints.

## Vercel Frontend → Local Backend

Vercel cannot connect to `localhost` on the Windows computer. The local backend must be exposed through a public credential-free HTTPS tunnel.

After the backend is healthy, run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\connect-local-backend.ps1
```

The script performs these checks and changes:

1. Confirms `http://127.0.0.1:8080/actuator/health` is `UP`.
2. Reuses an existing ngrok tunnel or starts `ngrok http 8080`.
3. Confirms the public HTTPS health endpoint is reachable.
4. Writes the local Next.js BFF configuration to `apps/web/.env.local`.
5. Updates the GitHub repository variable `PRODUCTION_BASE_URL` when GitHub CLI is authenticated.
6. Updates Vercel Production variables `BACKEND_API_BASE_URL` and `BACKEND_REQUEST_TIMEOUT_MS` when Vercel CLI is authenticated.

To configure Vercel and deploy the current `apps/web` source in the same command, use:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\windows\connect-local-backend.ps1 `
  -DeployVercelProduction
```

The production variables are:

```dotenv
BACKEND_API_BASE_URL=https://<active-ngrok-host>
BACKEND_REQUEST_TIMEOUT_MS=15000
```

Production browser traffic remains same-origin:

```text
https://snad-app.vercel.app/api/platform/api/v1/**
```

## End-to-End Verification

Run all three checks after the tunnel and Vercel variable are active:

```powershell
# Direct local backend
Invoke-WebRequest http://127.0.0.1:8080/actuator/health

# Public tunnel backend
Invoke-WebRequest https://<active-ngrok-host>/actuator/health `
  -Headers @{ "ngrok-skip-browser-warning" = "any-value" }

# Vercel BFF authentication boundary; expected HTTP 401 without a token
Invoke-WebRequest https://snad-app.vercel.app/api/platform/api/v1/auth/me `
  -SkipHttpErrorCheck
```

Acceptance result:

```text
LOCAL_BACKEND_HEALTH=200
PUBLIC_TUNNEL_HEALTH=200
VERCEL_BFF_AUTH_BOUNDARY=401
```

`502` means the tunnel or local backend is unreachable. `503` means `BACKEND_API_BASE_URL` is missing or invalid. `404` means the BFF route is absent from the Vercel build.

## Backend Production Environment

The local backend production profile still requires:

```dotenv
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<host>:5432/<db>
DATABASE_USERNAME=<username>
DATABASE_PASSWORD=<password>
JWT_SECRET=<32+ byte secret>
BOOTSTRAP_ENABLED=false
SANAD_CONTROL_PLANE_TENANT_ID=<valid tenant UUID>
SANAD_CORS_ALLOWED_ORIGINS=https://snad-app.vercel.app,http://localhost:3000
```

The BFF is the primary browser integration boundary. The CORS allowlist remains useful for controlled direct development and diagnostics.

## Operational Constraint

Because the backend runs on a local computer, the public application depends on all of the following remaining active:

- The Windows computer is powered on and connected to the internet.
- PostgreSQL and the SANAD backend process are running.
- The ngrok process is running.
- Vercel `BACKEND_API_BASE_URL` points to the current tunnel URL.
- GitHub `PRODUCTION_BASE_URL` points to the same current tunnel URL.

A changing temporary tunnel URL must be updated in Vercel and GitHub. Running `connect-local-backend.ps1` again performs those updates. A reserved/static tunnel domain removes that repeated update.
