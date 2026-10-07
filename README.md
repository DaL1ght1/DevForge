# DevForge

DevForge is a platform that generates developer services from reusable templates. It uses a Next.js frontend, a Spring Boot backend, a Go provisioning worker, PostgreSQL, Keycloak, Kafka, and GitHub integration.

## Run locally

### 1. Configure the backend

Create the backend environment file:

```powershell
Copy-Item backend\.env.example backend\.env
```

Fill in the required database, Keycloak, administrator, and GitHub values in `backend\.env`.

### 2. Start the backend stack

From the repository root:

```powershell
Set-Location backend
docker compose up --build -d 
```

This starts the Spring Boot API, Go provisioner, PostgreSQL, Keycloak, and Kafka.

### 3. Configure and start the frontend

Create the frontend environment file:

```powershell
Set-Location ..\frontend
Copy-Item .env.local.example .env.local
npm install
npm run dev
```

Open the application at:

```text
http://localhost:3000
```

The main services are available at:

```text
Swagger API: http://localhost:8080/api/v1/swagger-ui/index.html
Keycloak admin: http://localhost:8180
Provisioner health: http://localhost:8081/health
```

The frontend uses `localhost` URLs because it runs outside Docker. Internal containers communicate through Docker service names such as `kafka`, `postgres`, and `keycloak-db`.

## Useful commands

```powershell
# Stop the backend stack
Set-Location ..\backend
docker compose down

# Run frontend checks
Set-Location ..\frontend
npm run lint
npm run build
```

See [CONTRIBUTE.md](CONTRIBUTE.md) for template requirements and contribution guidelines.
