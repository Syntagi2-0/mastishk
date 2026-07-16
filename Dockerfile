FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S syntagi && adduser -S syntagi -G syntagi
WORKDIR /app
COPY --from=build /workspace/target/syntagi-lite-*.jar app.jar
USER syntagi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
