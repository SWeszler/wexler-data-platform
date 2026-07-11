FROM sbtscala/scala-sbt:eclipse-temurin-focal-11.0.17_8_1.8.2_2.12.17 AS builder

WORKDIR /app
COPY . /app
RUN sbt clean assembly

FROM maven:3.9.9-eclipse-temurin-11 AS s3-dependencies

RUN mvn --batch-mode dependency:copy \
    -Dartifact=org.apache.hadoop:hadoop-aws:3.3.4:jar \
    -DoutputDirectory=/s3-jars \
    -Dmdep.stripVersion=true && \
    mvn --batch-mode dependency:copy \
    -Dartifact=com.amazonaws:aws-java-sdk-bundle:1.12.262:jar \
    -DoutputDirectory=/s3-jars \
    -Dmdep.stripVersion=true

FROM apache/spark:3.5.6-scala2.12-java11-python3-ubuntu

USER root
RUN mkdir -p /opt/adam && chown 185:185 /opt/adam
COPY --from=builder /app/target/scala-2.12/*-assembly-*.jar /opt/adam/adam-pipeline.jar
COPY --from=s3-dependencies /s3-jars/*.jar /opt/spark/jars/
RUN chown 185:185 /opt/adam/adam-pipeline.jar

USER 185
ENTRYPOINT ["/opt/entrypoint.sh"]
