package com.example.eventstore

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.parquet.avro.AvroParquetWriter
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.pow

class ParquetS3Writer(private val config: Config) {

    private val logger = LoggerFactory.getLogger(ParquetS3Writer::class.java)

    private val avroSchema: Schema = Schema.Parser().parse(
        """
        {
          "type": "record",
          "name": "Event",
          "namespace": "com.example.eventstore",
          "fields": [
            {"name": "event_id",   "type": "string"},
            {"name": "event_type", "type": "string"},
            {"name": "source",     "type": "string"},
            {"name": "message",    "type": "string"},
            {"name": "created_at", "type": "string"},
            {"name": "stored_at",  "type": "string"},
            {"name": "event_date", "type": "string"}
          ]
        }
        """.trimIndent()
    )

    private val hadoopConf = Configuration().apply {
        set("fs.s3a.endpoint", config.s3Endpoint)
        set("fs.s3a.access.key", config.awsAccessKeyId)
        set("fs.s3a.secret.key", config.awsSecretAccessKey)
        set("fs.s3a.path.style.access", "true")
        set("fs.s3a.connection.ssl.enabled", "false")
        set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        set("fs.s3a.connection.establish.timeout", "10000")
        set("fs.s3a.connection.timeout", "30000")
        set("fs.s3a.attempts.maximum", "3")
    }

    private val timestampParsers = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )

    private val dateOnlyRegex = Regex("""\d{4}-\d{2}-\d{2}""")

    fun write(records: List<EventRecord>) {
        if (records.isEmpty()) return

        val storedAt = Instant.now().toString()
        val grouped = records.groupBy { extractEventDate(it.created_at) }

        for ((eventDate, group) in grouped) {
            writeWithRetry(group, eventDate, storedAt)
        }
    }

    private fun writeWithRetry(
        records: List<EventRecord>,
        eventDate: String,
        storedAt: String
    ) {
        for (attempt in 1..3) {
            val fileName = "events_${UUID.randomUUID()}.parquet"
            val tempPath = Path("${config.s3TempPath}/event_date=$eventDate/$fileName")
            val finalPath = Path("${config.s3OutputPath}/event_date=$eventDate/$fileName")
            val fs = finalPath.getFileSystem(hadoopConf)

            try {
                writeFile(tempPath, records, eventDate, storedAt)
                moveIntoTable(fs, tempPath, finalPath)
                logger.info("Published {} records to {}", records.size, finalPath)
                return
            } catch (e: Exception) {
                logger.warn(
                    "Failed to publish {} records for event_date={} (attempt {}/3): {}",
                    records.size, eventDate, attempt, e.message
                )
                cleanupTemp(fs, tempPath)

                if (attempt == 3) {
                    throw e
                }

                Thread.sleep(1000L * 2.0.pow((attempt - 1).toDouble()).toLong())
            }
        }
    }

    private fun moveIntoTable(fs: FileSystem, tempPath: Path, finalPath: Path) {
        if (fs.exists(finalPath)) {
            throw IllegalStateException("Final Parquet path already exists: $finalPath")
        }

        if (!fs.rename(tempPath, finalPath)) {
            throw IllegalStateException("Failed to move completed Parquet file from $tempPath to $finalPath")
        }
    }

    private fun cleanupTemp(fs: FileSystem, tempPath: Path) {
        try {
            if (fs.exists(tempPath)) {
                fs.delete(tempPath, false)
            }
        } catch (cleanupError: Exception) {
            logger.warn("Failed to clean temporary Parquet path {}: {}", tempPath, cleanupError.message)
        }
    }

    private fun writeFile(
        outputPath: Path,
        records: List<EventRecord>,
        eventDate: String,
        storedAt: String
    ) {
        logger.info("Writing {} records to {}", records.size, outputPath)

        val writer = AvroParquetWriter.builder<GenericRecord>(outputPath)
            .withSchema(avroSchema)
            .withConf(hadoopConf)
            .build()

        writer.use { w ->
            for (event in records) {
                val record = GenericData.Record(avroSchema).apply {
                    put("event_id", event.event_id)
                    put("event_type", event.event_type)
                    put("source", event.source)
                    put("message", event.message)
                    put("created_at", event.created_at)
                    put("stored_at", storedAt)
                    put("event_date", eventDate)
                }
                w.write(record)
            }
        }

        logger.info("Wrote {} records to {}", records.size, outputPath)
    }

    private fun extractEventDate(createdAt: String): String {
        for (formatter in timestampParsers) {
            try {
                val parsed = LocalDateTime.parse(createdAt, formatter)
                return parsed.toLocalDate().toString()
            } catch (_: Exception) {
                // try next format
            }
        }

        // Try extracting date prefix
        val prefix = createdAt.take(10)
        if (dateOnlyRegex.matches(prefix)) {
            return prefix
        }

        // Fall back to today
        return LocalDate.now().toString()
    }

    fun close() {
        logger.info("ParquetS3Writer closed")
    }
}
