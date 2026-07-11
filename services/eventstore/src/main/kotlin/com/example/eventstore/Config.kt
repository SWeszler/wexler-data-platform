package com.example.eventstore

import java.util.Locale

data class Config(
    val bootstrapServers: String,
    val topic: String,
    val s3Endpoint: String,
    val s3BasePath: String,
    val awsAccessKeyId: String,
    val awsSecretAccessKey: String,
    val trinoJdbcUrl: String,
    val flushRecordThreshold: Int,
    val flushIntervalSeconds: Long,
    val hiveSchema: String,
    val tableName: String,
    val consumerGroup: String
) {
    val s3OutputPath: String = "$s3BasePath/$tableName"
    val s3TempPath: String = "$s3BasePath/_tmp/$tableName"

    companion object {
        fun fromEnv(): Config {
            val topic = System.getenv("KAFKA_TOPIC") ?: "spark-smoke-events"
            val tableName = System.getenv("TABLE_NAME") ?: topic.toHiveIdentifier()
            val consumerGroup = System.getenv("CONSUMER_GROUP") ?: "eventstore.$tableName"

            val config = Config(
                bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS")
                    ?: "wexler-kafka-kafka-bootstrap.data.svc.cluster.local:9092",
                topic = topic,
                s3Endpoint = System.getenv("S3_ENDPOINT")
                    ?: "http://minio.data.svc.cluster.local:9000",
                s3BasePath = System.getenv("S3_BASE_PATH")
                    ?: "s3a://warehouse/eventstore",
                awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID") ?: "",
                awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY") ?: "",
                trinoJdbcUrl = System.getenv("TRINO_JDBC_URL")
                    ?: "jdbc:trino://trino.data.svc.cluster.local:8080/hive/default",
                flushRecordThreshold = (System.getenv("FLUSH_RECORD_THRESHOLD") ?: "1000").toInt(),
                flushIntervalSeconds = (System.getenv("FLUSH_INTERVAL_SECONDS") ?: "60").toLong(),
                hiveSchema = System.getenv("HIVE_SCHEMA") ?: "eventstore",
                tableName = tableName,
                consumerGroup = consumerGroup
            )

            require(config.topic.isNotBlank()) { "KAFKA_TOPIC must not be blank" }
            require(Regex("[a-zA-Z][a-zA-Z0-9_]*").matches(config.hiveSchema)) {
                "HIVE_SCHEMA must be a valid Hive identifier"
            }
            require(Regex("[a-zA-Z][a-zA-Z0-9_]*").matches(config.tableName)) {
                "TABLE_NAME must be a valid Hive identifier"
            }
            require(config.consumerGroup.isNotBlank()) { "CONSUMER_GROUP must not be blank" }
            require(config.flushRecordThreshold > 0) { "FLUSH_RECORD_THRESHOLD must be greater than 0" }
            require(config.flushIntervalSeconds > 0) { "FLUSH_INTERVAL_SECONDS must be greater than 0" }

            return config
        }
    }
}

private fun String.toHiveIdentifier(): String {
    val normalized = lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    val safeName = normalized.ifBlank { "topic" }
    return if (safeName.first().isLetter()) safeName else "topic_$safeName"
}
