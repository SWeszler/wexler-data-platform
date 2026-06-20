# Log Analyzer Scala Spark Job

This job sessionizes web server logs (CLF format) by IP, computes session durations, and writes results to Hive and Parquet.

## Usage

Build the assembly JAR:

```sh
cd jobs/log-analyzer-scala
sbt assembly
```

### Arguments
- `--input` (default: `/data/logs/web_server_logs.txt`): Input log file (HDFS path)
- `--hiveDb` (default: `default`): Hive database for output tables
- `--sessionGapMinutes` (default: 30): Session gap in minutes
- `--outputFormat` (default: `parquet`): Output format for Parquet files
- `--overwrite`: Overwrite output tables/files

### Run with Docker

```sh
docker build --platform linux/amd64 -t log-analyzer-scala .
```

Run in client deploy mode. The driver runs inside the temporary
`log-analyzer-scala` submit container, while executors run on the Spark workers.

```sh
docker run --rm \
  --network wexler-data-platform_default \
  -e SPARK_APPLICATION_ARGS="--input hdfs://namenode:9000/data/logs/web_server_logs.txt --hiveDb default --sessionGapMinutes 30" \
  log-analyzer-scala
```

Run in cluster deploy mode. The submit container publishes its bundled JAR to
`hdfs://namenode:9000/apps/log-analyzer-scala/log-analyzer-scala.jar`, then
Spark starts the driver on a worker.

```sh
docker run --rm \
  --network wexler-data-platform_default \
  -e SPARK_APPLICATION_DEPLOY_MODE=cluster \
  -e SPARK_APPLICATION_ARGS="--input hdfs://namenode:9000/data/logs/web_server_logs.txt --hiveDb default --sessionGapMinutes 30" \
  log-analyzer-scala
```

You do not need to set `SPARK_APPLICATION_JAR_LOCATION` for the normal local
cluster run. If you provide a custom value, use an `hdfs://` path; the
container will upload its bundled JAR there before submitting the job.

While the submit container is still running, inspect its logs with:

```sh
docker logs -f log-analyzer-submit
```

After the submit container exits, track the Spark application from the Spark
Master UI at `http://localhost:8080/` and the worker/driver logs.

## Output
- Hive tables: `<hiveDb>.sessions` (partitioned by job_run_date), `<hiveDb>.session_summary` (partitioned by job_run_date)
- Parquet: `/data/output/sessionization/` (partitioned by job_run_date)

Note: Each run adds job_run_id (UUID), created_at, updated_at, and job_run_date columns; tables are partitioned by job_run_date.
