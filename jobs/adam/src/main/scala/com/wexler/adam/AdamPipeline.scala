package com.wexler.adam

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, lit}
import org.bdgenomics.adam.ds.ADAMContext

final case class Config(
    input: String = "",
    output: String = "",
    database: String = "genomics",
    table: String = "alignments",
    batchId: String = ""
)

object AdamPipeline {
  def main(args: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Config]("adam-pipeline") {
      head("adam-pipeline")
      opt[String]("input").required().action((value, config) => config.copy(input = value))
      opt[String]("output").required().action((value, config) => config.copy(output = value))
      opt[String]("database").action((value, config) => config.copy(database = value))
      opt[String]("table").action((value, config) => config.copy(table = value))
      opt[String]("batch-id").required().action((value, config) => config.copy(batchId = value))
    }

    parser.parse(args, Config()) match {
      case Some(config) => run(config)
      case None => sys.exit(1)
    }
  }

  private def run(config: Config): Unit = {
    require(config.database.matches("[A-Za-z_][A-Za-z0-9_]*"), "Invalid database name")
    require(config.table.matches("[A-Za-z_][A-Za-z0-9_]*"), "Invalid table name")
    require(config.batchId.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"), "Invalid batch ID")
    require(!config.output.contains("'"), "Output URI cannot contain a quote")

    val spark = SparkSession.builder()
      .appName("adam-publish-alignments")
      .enableHiveSupport()
      .getOrCreate()

    try {
      val adamContext = new ADAMContext(spark.sparkContext)
      val alignments = adamContext.loadAlignments(config.input)
      val qualifiedTable = s"`${config.database}`.`${config.table}`"
      val batch = alignments.dataset.withColumn("batch_id", lit(config.batchId))
      val batchCount = batch.count()
      require(batchCount > 0, s"Refusing to publish an empty batch from ${config.input}")

      spark.sql(s"CREATE DATABASE IF NOT EXISTS `${config.database}`")
      val tableExists = spark.catalog.tableExists(config.database, config.table)
      if (!tableExists) {
        batch.limit(0).write
          .format("parquet")
          .partitionBy("batch_id")
          .option("path", config.output)
          .saveAsTable(s"${config.database}.${config.table}")
      } else {
        val metadata = spark.sql(s"DESCRIBE EXTENDED $qualifiedTable")
        val location = metadata
          .filter(col("col_name") === "Location")
          .select("data_type")
          .head()
          .getString(0)
        require(location == config.output, s"Table location $location does not match ${config.output}")

        val expectedColumns = batch.schema.fieldNames.toSeq
        val actualColumns = spark.table(qualifiedTable).schema.fieldNames.toSeq
        require(actualColumns == expectedColumns, "Incoming schema does not match the Hive table")
      }

      val existingBatchCount = spark.table(qualifiedTable)
        .filter(col("batch_id") === config.batchId)
        .count()

      if (existingBatchCount == 0) {
        batch.write.mode("append").insertInto(s"${config.database}.${config.table}")
      } else {
        require(existingBatchCount == batchCount, s"Batch ${config.batchId} exists with an unexpected row count")
      }

      val publishedBatchCount = spark.table(qualifiedTable)
        .filter(col("batch_id") === config.batchId)
        .count()
      require(publishedBatchCount == batchCount, s"Published batch has $publishedBatchCount rows; expected $batchCount")
      println(s"Published batch ${config.batchId} with $publishedBatchCount row(s) to $qualifiedTable")
    } finally {
      spark.stop()
    }
  }
}
