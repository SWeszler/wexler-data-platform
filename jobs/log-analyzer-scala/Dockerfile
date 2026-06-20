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

ARG SPARK_RUNTIME_VERSION=3.0.0
ARG SPARK_RUNTIME_PACKAGE=spark-${SPARK_RUNTIME_VERSION}-bin-hadoop3.2
ENV SPARK_HOME=/opt/spark-${SPARK_RUNTIME_VERSION}

RUN mkdir -p "${SPARK_HOME}" \
	&& curl -fsSL "https://archive.apache.org/dist/spark/spark-${SPARK_RUNTIME_VERSION}/${SPARK_RUNTIME_PACKAGE}.tgz" \
		| tar -xz -C "${SPARK_HOME}" --strip-components=1 \
	&& chown -R 185:185 "${SPARK_HOME}"

# Copy assembled jar from builder
COPY --from=builder /app/target/scala-2.12/*-assembly-*.jar /app/target/

# Copy submit helpers
COPY run.sh submit-client.sh submit-cluster.sh /
RUN chmod +x /run.sh /submit-client.sh /submit-cluster.sh
COPY hive-site.xml /opt/spark-3.0.0/conf/hive-site.xml
COPY spark-defaults.conf /opt/spark-3.0.0/conf/spark-defaults.conf

ENV SPARK_APPLICATION_MAIN_CLASS=com.example.SessionAnalysis

USER 185
ENTRYPOINT []
CMD ["/run.sh"]
