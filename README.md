# Syntagi Lite

Java 21 and Spring Boot 3 modular-monolith foundation for queue management and appointments.

## Prerequisites

- Java 21
- Docker (for PostgreSQL), or a PostgreSQL 17 instance

## Run locally

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html`; health is available at
`http://localhost:8080/actuator/health`.

The `local` profile is the default. The `dev` and `prod` profiles require `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, and a JWT HMAC `JWT_SECRET` of at least 32 bytes.

No application tables or business endpoints are included yet. The initial Flyway migration only
establishes migration history; entity migrations will be added with their business features.
