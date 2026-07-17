# Syntagi Lite

Java 21 and Spring Boot 3 modular-monolith foundation for queue management and appointments.

## Prerequisites

- Java 21
- Docker (for PostgreSQL), or a PostgreSQL 17 instance

## Run locally

```bash
export DB_USERNAME=syntagi
export DB_PASSWORD='choose-a-local-database-password'
export JWT_SECRET='choose-a-random-jwt-secret-with-at-least-32-bytes'
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html`; health is available at
`http://localhost:8080/actuator/health`.

No profile is activated implicitly. The `local`, `dev`, and `prod` profiles require
`DB_USERNAME`, `DB_PASSWORD`, and a JWT HMAC `JWT_SECRET` of at least 32 characters.
`dev` and `prod` also require `DB_URL`; local defaults it to the PostgreSQL instance on
`localhost:5432`. Production additionally requires `CORS_ALLOWED_ORIGIN_PATTERNS`.

To start the complete stack, set the same environment variables and run
`docker compose up --build`. The application container reports readiness through
`/actuator/health/readiness` after PostgreSQL, Flyway, and Hibernate validation succeed.

Flyway owns the complete schema and Hibernate runs with `ddl-auto=validate`. Swagger is
available in local and dev environments and is disabled in production.
