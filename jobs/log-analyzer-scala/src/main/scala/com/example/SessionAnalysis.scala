package com.example

import org.apache.spark.sql.{SparkSession, DataFrame, Row}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.types._

object SessionAnalysis {

  // Regular CLF: IP - user [dd/MMM/yyyy:HH:mm:ss Z] "REQUEST" STATUS SIZE
  val clfRegex = """^(\S+) (\S+) (\S+) \[(.*?)\] \"(.*?)\" (\d{3}) (\S+)""".r

  case class Config(
    input: String = "/data/logs/web_server_logs.txt",
    hiveDb: String = "default",
    sessionGapMinutes: Int = 30,
    outputFormat: String = "parquet",
    overwrite: Boolean = false
  )

  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Config]("SessionAnalysis") {
      head("SessionAnalysis", "1.0")
      opt[String]("input").action((x, c) => c.copy(input = x)).text("Input log file path")
      opt[String]("hiveDb").action((x, c) => c.copy(hiveDb = x)).text("Hive database name (default: default)")
      opt[Int]("sessionGapMinutes").action((x, c) => c.copy(sessionGapMinutes = x)).text("Session gap in minutes")
      opt[String]("outputFormat").action((x, c) => c.copy(outputFormat = x)).text("Output format (parquet, etc)")
      opt[Unit]("overwrite").action((_, c) => c.copy(overwrite = true)).text("Overwrite output")
    }

    parser.parse(args, Config()) match {
      case Some(config) => runJob(config)
      case None => System.err.println("Invalid arguments"); System.exit(1)
    }
  }

  def runJob(config: Config): Unit = {
    val spark = SparkSession.builder()
      .appName("SessionAnalysis")
      .enableHiveSupport()
      .getOrCreate()

    import spark.implicits._

    val raw = spark.read.textFile(config.input)

    val parsed = raw.flatMap { line =>
      clfRegex.findFirstMatchIn(line).map { m =>
        // groups: 1=ip, 4=datetime, 5=request, 6=status,7=size
        (m.group(1), m.group(4), m.group(5), m.group(6), m.group(7))
      }
    }.toDF("ip", "datetime", "request", "status", "size")
      .withColumn("ts", to_timestamp(col("datetime"), "dd/MMM/yyyy:HH:mm:ss Z"))
      .filter(col("ts").isNotNull)
      .withColumn("ts_unix", unix_timestamp(col("ts")))

    val parsedCount = parsed.count()
    println(s"Parsed $parsedCount rows out of ${raw.count()} input lines")
    if (parsedCount == 0) {
      throw new RuntimeException("No rows matched the log regex — aborting before writing empty tables")
    }

    // Window to compute gap between consecutive requests per IP
    val w = Window.partitionBy("ip").orderBy("ts")

    val withLag = parsed.withColumn("prev_ts", lag(col("ts"), 1).over(w))
      .withColumn("prev_ts_unix", unix_timestamp(col("prev_ts")))
      .withColumn("diff_seconds", (col("ts_unix") - col("prev_ts_unix")))
      .withColumn("new_session", when(col("prev_ts").isNull || col("diff_seconds") > config.sessionGapMinutes * 60, 1).otherwise(0))

    // cumulative sum of new_session to assign session index per IP
    val withSessionIndex = withLag.withColumn("session_index",
      sum(col("new_session")).over(Window.partitionBy("ip").orderBy("ts").rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      .withColumn("session_id", concat(col("ip"), lit("_"), col("session_index")))

    // aggregate to session level
    val sessions = withSessionIndex.groupBy("session_id", "ip")
      .agg(
        min(col("ts")).as("start_ts"),
        max(col("ts")).as("end_ts")
      ).withColumn("duration_seconds", (unix_timestamp(col("end_ts")) - unix_timestamp(col("start_ts"))).cast("long"))

    // summary
    val summary = sessions.agg(avg(col("duration_seconds")).as("avg_session_duration_seconds"), count(lit(1)).as("session_count"))

    // Ensure database exists
    spark.sql(s"CREATE DATABASE IF NOT EXISTS ${config.hiveDb}")

    val mode = if (config.overwrite) "overwrite" else "append"

    // Add run metadata to avoid id collisions and enable partitioned storage
    val jobRunId = java.util.UUID.randomUUID().toString
    val jobRunDate = java.time.LocalDate.now().toString
    val jobRunTimestamp = java.sql.Timestamp.from(java.time.Instant.now())

    val sessionsWithMeta = sessions
      .withColumn("job_run_id", lit(jobRunId))
      .withColumn("created_at", lit(jobRunTimestamp))
      .withColumn("updated_at", lit(jobRunTimestamp))
      .withColumn("job_run_date", to_date(lit(jobRunDate)))

    val summaryWithMeta = summary
      .withColumn("job_run_id", lit(jobRunId))
      .withColumn("created_at", lit(jobRunTimestamp))
      .withColumn("updated_at", lit(jobRunTimestamp))
      .withColumn("job_run_date", to_date(lit(jobRunDate)))

    // Write sessions partitioned by job_run_date
    sessionsWithMeta.write.mode(mode).partitionBy("job_run_date").format("parquet").saveAsTable(s"${config.hiveDb}.sessions")

    // Write summary partitioned by job_run_date
    summaryWithMeta.write.mode(mode).partitionBy("job_run_date").format("parquet").saveAsTable(s"${config.hiveDb}.session_summary")

    // Also write Parquet to HDFS path for convenience (partitioned)
    val outBase = "/data/output/sessionization"
    sessionsWithMeta.write.mode(mode).partitionBy("job_run_date").parquet(s"$outBase/sessions")
    summaryWithMeta.write.mode(mode).partitionBy("job_run_date").parquet(s"$outBase/summary")

    // Show result
    summaryWithMeta.show(false)

    spark.stop()
  }
}
