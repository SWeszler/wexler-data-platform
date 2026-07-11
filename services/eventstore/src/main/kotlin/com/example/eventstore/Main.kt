package com.example.eventstore

import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("eventstore")
    val config = Config.fromEnv()
    logger.info("eventstore starting - topic={} table={}.{}", config.topic, config.hiveSchema, config.tableName)

    val hiveSync = HiveSync(config)
    hiveSync.ensureSchemaAndTable()
    hiveSync.syncPartitions()
    HealthFiles.markReady()

    val writer = ParquetS3Writer(config)
    val consumer = EventConsumer(config, writer, hiveSync)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown hook triggered")
        consumer.shutdown()
    })

    consumer.run()

    writer.close()
    hiveSync.close()
    logger.info("eventstore stopped")
}
