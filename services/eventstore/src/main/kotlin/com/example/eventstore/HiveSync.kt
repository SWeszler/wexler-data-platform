package com.example.eventstore

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import kotlin.math.pow

class HiveSync(private val config: Config) {

    private val logger = LoggerFactory.getLogger(HiveSync::class.java)

    init {
        DriverManager.setLoginTimeout(10)
    }

    private fun getConnection(): Connection {
        val props = Properties().apply {
            setProperty("user", "eventstore")
        }
        return DriverManager.getConnection(config.trinoJdbcUrl, props)
    }

    fun ensureSchemaAndTable() {
        val createSchema = """
            CREATE SCHEMA IF NOT EXISTS ${config.hiveSchema}
            WITH (location = '${config.s3BasePath}/')
        """.trimIndent()

        val createTable = """
            CREATE TABLE IF NOT EXISTS ${config.hiveSchema}.${config.tableName} (
                event_id    VARCHAR,
                event_type  VARCHAR,
                source      VARCHAR,
                message     VARCHAR,
                created_at  VARCHAR,
                stored_at   VARCHAR,
                event_date  VARCHAR
            )
            WITH (
                format = 'PARQUET',
                partitioned_by = ARRAY['event_date'],
                external_location = '${config.s3OutputPath}/'
            )
        """.trimIndent()

        for (attempt in 1..5) {
            try {
                getConnection().use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute(createSchema)
                        logger.info("Schema '{}' ensured", config.hiveSchema)
                    }
                    conn.createStatement().use { stmt ->
                        stmt.execute(createTable)
                        logger.info("Table '{}.{}' ensured", config.hiveSchema, config.tableName)
                    }
                }
                return
            } catch (e: Exception) {
                logger.warn(
                    "Failed to ensure schema/table (attempt {}/5): {}",
                    attempt, e.message
                )
                if (attempt < 5) {
                    Thread.sleep(5000)
                }
            }
        }

        throw RuntimeException(
            "Failed to create schema/table after 5 attempts"
        )
    }

    fun syncPartitions() {
        for (attempt in 1..3) {
            try {
                getConnection().use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.queryTimeout = 30
                        stmt.execute(
                            "CALL system.sync_partition_metadata('${config.hiveSchema}', '${config.tableName}', 'ADD')"
                        )
                        logger.info("Partition metadata synced for {}.{}", config.hiveSchema, config.tableName)
                    }
                }
                return
            } catch (e: Exception) {
                logger.warn(
                    "Failed to sync partitions for {}.{} (attempt {}/3): {}",
                    config.hiveSchema, config.tableName, attempt, e.message
                )

                if (attempt == 3) {
                    throw RuntimeException(
                        "Failed to sync partitions for ${config.hiveSchema}.${config.tableName} after 3 attempts",
                        e
                    )
                }

                Thread.sleep(1000L * 2.0.pow((attempt - 1).toDouble()).toLong())
            }
        }
    }

    fun close() {
        // Connections are created per call, nothing to close
    }
}
