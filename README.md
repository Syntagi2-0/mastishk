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
`dev` requires `DB_URL`; local defaults it to the PostgreSQL instance on
`localhost:5432`. The production container accepts Render's standard `DATABASE_URL`
and additionally requires `CORS_ALLOWED_ORIGIN_PATTERNS`.

To start the complete stack, set the same environment variables and run
`docker compose up --build`. The application container reports readiness through
`/actuator/health/readiness` after PostgreSQL, Flyway, and Hibernate validation succeed.

Flyway owns the complete schema and Hibernate runs with `ddl-auto=validate`. Swagger is
available in local and dev environments and is disabled in production.

## Deploy to Render

The repository includes a Render Blueprint for the Docker web service in Singapore.
Production data is stored in a separate Supabase Postgres project.

1. Create a Supabase project in the Singapore region and save its database password.
2. Because this application accesses Postgres only through the Spring backend, disable
   Supabase's Data API from its integration settings.
3. In the Supabase project, select **Connect > Session pooler** and copy the connection
   details. Use session mode on port `5432`, not transaction mode on port `6543`.
4. Push this repository to GitHub.
5. In Render, select **New > Blueprint**, connect the repository, and apply
   `render.yaml`.
6. Supply the prompted environment variables:
   - `DATABASE_URL`: the complete Supabase Session pooler connection string.
   - `DB_USERNAME`: the Session pooler user, usually `postgres.<project-ref>`.
   - `DB_PASSWORD`: the Supabase database password.
   - `CORS_ALLOWED_ORIGIN_PATTERNS`: both deployed frontend origins:
     `https://syntagi-business.web.app,https://syntagi-customer.web.app`. Multiple
     origins are comma-separated.
7. Wait for `/actuator/health/readiness` to report healthy. Flyway applies the database
   migrations automatically during application startup.

For a temporary frontend URL, update `CORS_ALLOWED_ORIGIN_PATTERNS` in the service's
Environment page and redeploy. Do not include a trailing slash in an exact origin.
