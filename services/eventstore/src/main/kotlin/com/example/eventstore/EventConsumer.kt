package com.example.eventstore

import kotlinx.serialization.json.Json
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

class EventConsumer(
    private val config: Config,
    private val writer: ParquetS3Writer,
    private val hiveSync: HiveSync
) {

    private val logger = LoggerFactory.getLogger(EventConsumer::class.java)

    private val json = Json { ignoreUnknownKeys = true }

    private val consumer: KafkaConsumer<String, String> = KafkaConsumer(
        Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers)
            put(ConsumerConfig.GROUP_ID_CONFIG, config.consumerGroup)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "30000")
            put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "15000")
        }
    )

    @Volatile
    var running = true

    private val buffer = mutableListOf<EventRecord>()
    private var lastFlushTime = System.currentTimeMillis()

    fun run() {
        consumer.subscribe(listOf(config.topic))
        logger.info("Subscribed to topic '{}' (group: {})", config.topic, config.consumerGroup)
        logger.info("Polling for events...")

        try {
            while (running) {
                val records = consumer.poll(Duration.ofSeconds(1))
                HealthFiles.markAlive()
                var malformedRecords = 0

                for (record in records) {
                    try {
                        val event = json.decodeFromString<EventRecord>(record.value())
                        buffer.add(event)
                    } catch (e: Exception) {
                        logger.warn(
                            "Failed to parse record topic={} partition={} offset={}: {}",
                            record.topic(), record.partition(), record.offset(), e.message
                        )
                        malformedRecords++
                    }
                }

                val elapsed = System.currentTimeMillis() - lastFlushTime
                val thresholdReached = buffer.size >= config.flushRecordThreshold
                val intervalReached = buffer.isNotEmpty() && elapsed >= config.flushIntervalSeconds * 1000L

                if (thresholdReached || intervalReached) {
                    flush()
                } else if (!records.isEmpty && buffer.isEmpty() && malformedRecords > 0) {
                    consumer.commitSync()
                    logger.info("Committed offsets for {} skipped malformed records", malformedRecords)
                }
            }
        } catch (_: WakeupException) {
            logger.info("Consumer wakeup received")
            if (buffer.isNotEmpty()) {
                flush()
            }
        } finally {
            consumer.close()
            logger.info("Consumer closed")
        }
    }

    private fun flush() {
        val count = buffer.size
        writer.write(buffer)
        hiveSync.syncPartitions()
        consumer.commitSync()
        logger.info("Flushed {} records", count)
        buffer.clear()
        lastFlushTime = System.currentTimeMillis()
    }

    fun shutdown() {
        running = false
        consumer.wakeup()
    }
}
