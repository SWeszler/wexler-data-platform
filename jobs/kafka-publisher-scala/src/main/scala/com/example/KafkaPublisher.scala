package com.example

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object KafkaPublisher {
  case class Config(
    bootstrapServers: String = "wexler-kafka-kafka-bootstrap.data.svc.cluster.local:9092",
    topic: String = "spark-smoke-events",
    eventCount: Int = 25
  )

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Config]("KafkaPublisher") {
      head("KafkaPublisher", "1.0")
      opt[String]("bootstrapServers")
        .action((x, c) => c.copy(bootstrapServers = x))
        .text("Kafka bootstrap servers")
      opt[String]("topic")
        .action((x, c) => c.copy(topic = x))
        .text("Kafka topic")
      opt[Int]("eventCount")
        .action((x, c) => c.copy(eventCount = x))
        .validate(x => if (x > 0) success else failure("eventCount must be greater than 0"))
        .text("Number of synthetic events to publish")
    }

    parser.parse(args, Config()) match {
      case Some(config) => runJob(config)
      case None =>
        System.err.println("Invalid arguments")
        System.exit(1)
    }
  }

  def runJob(config: Config): Unit = {
    val spark = SparkSession.builder()
      .appName("KafkaPublisher")
      .getOrCreate()

    import spark.implicits._

    val createdAt = java.sql.Timestamp.from(java.time.Instant.now())
    val events = (1 to config.eventCount).map { id =>
      (
        f"event-$id%05d",
        if (id % 2 == 0) "page_view" else "click",
        "spark-kafka-publisher",
        s"Synthetic Kafka smoke event $id",
        createdAt
      )
    }.toDF("event_id", "event_type", "source", "message", "created_at")

    val kafkaRows = events.select(
      col("event_id").cast("string").as("key"),
      to_json(struct(
        col("event_id"),
        col("event_type"),
        col("source"),
        col("message"),
        col("created_at")
      )).as("value")
    )

    kafkaRows.write
      .format("kafka")
      .option("kafka.bootstrap.servers", config.bootstrapServers)
      .option("topic", config.topic)
      .save()

    println(s"Published ${config.eventCount} synthetic events to Kafka topic ${config.topic}")
    println(s"Kafka bootstrap servers: ${config.bootstrapServers}")

    spark.stop()
  }
}
