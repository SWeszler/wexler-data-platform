# --- STAGE 1: Build ---
# Fixed: Using a valid, exact tag for Java 11, sbt, and Scala 2.12
FROM sbtscala/scala-sbt:eclipse-temurin-focal-11.0.17_8_1.8.2_2.12.17 AS builder

WORKDIR /app
COPY . /app

# Build the assembly JAR
RUN sbt clean assembly

# --- STAGE 2: Runtime ---
FROM apache/spark:3.5.6-scala2.12-java11-python3-ubuntu

USER root
WORKDIR /app

# Copy assembled jar from builder
COPY --from=builder /app/target/scala-2.12/*-assembly-*.jar /app/target/

RUN chown -R 185:185 /app

ENV SPARK_APPLICATION_MAIN_CLASS=com.example.SessionAnalysis

USER 185
ENTRYPOINT ["/opt/entrypoint.sh"]
