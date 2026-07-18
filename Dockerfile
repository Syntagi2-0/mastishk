FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S syntagi && adduser -S syntagi -G syntagi
WORKDIR /app
COPY --from=build /workspace/target/syntagi-lite-*.jar app.jar
COPY --chmod=755 docker-entrypoint.sh /app/docker-entrypoint.sh
USER syntagi
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=20s --retries=12 \
  CMD wget -q -O - "http://localhost:${PORT:-8080}/actuator/health/readiness" | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["/app/docker-entrypoint.sh"]
