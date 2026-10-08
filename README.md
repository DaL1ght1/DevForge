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

On Bash-compatible systems, From the repository root, start the entire application with:

```bash
make run
```

This starts the backend services and the frontend development server.


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

## Development setup

Install Lefthook, then install the repository hooks from the repository root:

```powershell
go install github.com/evilmartians/lefthook@latest
lefthook install
```

Run formatters manually by stack:

```powershell
Set-Location backend\provisioner
gofmt -w .
goimports -w .
golangci-lint run

Set-Location ..\..
Set-Location backend
.\mvnw spotless:apply

Set-Location ..\frontend
npm run format
```

The hooks format staged Go, Java, and frontend files before commits. Before pushes,
they run Go tests, fast Spring unit tests, and frontend lint, typecheck, and Vitest.
GitHub Actions runs formatting checks, linting, and tests for only the changed stack
on pushes and pull requests targeting `main`. Full Spring tests use Testcontainers
and therefore require Docker in CI.

See [CONTRIBUTING.md](CONTRIBUTING.md) for template requirements and contribution guidelines.
