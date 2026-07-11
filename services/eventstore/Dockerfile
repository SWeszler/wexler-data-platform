# Stage 1: Build
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/build/libs/eventstore-all.jar /app/eventstore.jar
ENTRYPOINT ["java", "-jar", "/app/eventstore.jar"]
