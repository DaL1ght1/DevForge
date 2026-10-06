# Spring Boot Maven Microservice

Production-oriented Spring Boot starter with Maven, validation-ready configuration,
container packaging, CI, and a smoke-test endpoint.

## Variables

- `SERVICE_NAME` - service name returned by `/api/v1/ping`
- `DATABASE_TYPE` - `POSTGRESQL`, `MYSQL`, or `NONE`
- `PACKAGE_NAME` - Java package used by the generated source
- `CLASS_NAME` - application class name

## Local development

```bash
./mvnw spring-boot:run
```

Run tests with:

```bash
./mvnw test
```

The generated service exposes `GET /api/v1/ping` and listens on port `8080`.