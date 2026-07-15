# SNAD Windows Persistent Self-Hosting

## Objective

Run the SNAD backend continuously on a Windows development/server machine and reconnect it automatically after Windows sign-in. The public API remains stable through a remotely-managed Cloudflare Tunnel while the frontend remains deployed on Vercel.

## Runtime topology

```text
Vercel frontend
      |
      | HTTPS
      v
Stable API hostname on Cloudflare
      |
      | Cloudflare Tunnel (outbound connector)
      v
cloudflared container -> backend container -> PostgreSQL container
```

The Docker stack uses:

- PostgreSQL 16 with a named persistent volume.
- Spring Boot with the `prod` profile.
- A Cloudflare Tunnel connector using a deployment-managed token.
- `restart: unless-stopped` for every service.
- A Windows logon task that restores the stack after Docker Desktop starts.
- A watchdog task that checks `/actuator/health` every two minutes.

## Important persistence rule

Do not use the `local` Spring profile for this setup. That profile uses an in-memory H2 database and loses its data when the process stops. The provided stack uses PostgreSQL and the `prod` profile.

## Prerequisites

1. Windows 10 or Windows 11.
2. Docker Desktop with WSL 2 integration enabled.
3. Docker Desktop configured to start when the user signs in, or left available for the startup script to launch.
4. A domain managed in Cloudflare.
5. A remotely-managed Cloudflare Tunnel.
6. The Vercel frontend project.

## One-time Cloudflare setup

In Cloudflare Zero Trust:

1. Create a tunnel named `snad-local-backend`.
2. Copy the tunnel token. Do not commit it or paste it into repository files.
3. Add a public hostname, for example `api.example.com`.
4. Configure its service as:

```text
Type: HTTP
URL:  http://backend:8080
```

`backend` is the Docker Compose service name. Do not use `localhost` in the Cloudflare dashboard because `cloudflared` runs in its own container.

## Install automatic startup

Open PowerShell as Administrator from the repository root:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\windows\install-snad-autostart.ps1 `
  -PublicBackendUrl "https://api.example.com" `
  -VercelOrigins "https://snad-app.vercel.app"
```

The installer asks for the Cloudflare Tunnel token using hidden input. It then:

1. Generates the PostgreSQL password, JWT secret, and CRM encryption key.
2. Writes `deploy/self-hosted/.env`.
3. Restricts the file ACL to the current administrator, SYSTEM, and Administrators.
4. Builds and starts the Docker stack.
5. Registers `SNAD Stack Startup` at Windows sign-in.
6. Registers `SNAD Stack Watchdog` every two minutes.
7. Checks local and public health endpoints.

## Vercel configuration

Set the frontend backend/API base URL to the stable Cloudflare hostname rather than a temporary tunnel URL, for example:

```text
https://api.example.com
```

Use the environment-variable name already consumed by the frontend API client. Apply it to Production and Preview as required, then create a new Vercel deployment. Vercel does not apply changed environment variables to deployments that already exist.

## Health checks

Local:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

Public:

```powershell
Invoke-RestMethod https://api.example.com/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Operations

Start or rebuild manually:

```powershell
.\scripts\windows\start-snad-stack.ps1 -Build
```

Inspect containers:

```powershell
cd deploy\self-hosted
docker compose --env-file .env -f docker-compose.windows.yml ps
```

Inspect backend logs:

```powershell
docker logs --tail 200 snad-backend
```

Inspect tunnel logs:

```powershell
docker logs --tail 200 snad-cloudflared
```

Runtime logs written by the Windows scripts:

```text
logs/selfhosted/startup.log
logs/selfhosted/watchdog.log
```

## Restart behavior

- Container crash: Docker restarts the container automatically.
- Backend health failure: the watchdog attempts `docker compose up`, then restarts the backend and tunnel connector.
- Windows restart: the stack starts after the configured Windows user signs in and Docker Desktop becomes ready.
- Internet interruption: the Cloudflare connector reconnects when connectivity returns.
- Device powered off: Vercel remains online, but backend API calls are unavailable until the Windows device starts and signs in.

## Removal

Preserve PostgreSQL data:

```powershell
.\scripts\windows\uninstall-snad-autostart.ps1
```

Permanently remove the database volume and generated environment file:

```powershell
.\scripts\windows\uninstall-snad-autostart.ps1 `
  -RemoveDatabaseVolume `
  -RemoveEnvironmentFile
```

The second command permanently deletes local database data.
