# Log Analyzer Scala Spark Job

This job sessionizes web server logs (CLF format) by IP, computes session durations, and writes results to Hive and Parquet.

## Usage

### Arguments
- `--input` (default: `s3a://logs/web_server_logs.txt`): Input log file (MinIO/S3 path)
- `--hiveDb` (default: `default`): Hive database for output tables
- `--sessionGapMinutes` (default: 30): Session gap in minutes
- `--outputFormat` (default: `parquet`): Output format for Parquet files
- `--overwrite`: Overwrite output tables/files

### Run in Kubernetes (Minikube)

Build the image and load it into minikube:

```sh
docker build --platform linux/arm64 -t log-analyzer-scala .
minikube image load log-analyzer-scala
```

Submit the job using the SparkOperator manifest:

```sh
kubectl apply -f sparkapplication.yaml
```

Track the application:

```sh
kubectl get sparkapplication -n spark --watch
kubectl logs -n spark log-analyzer-scala-driver
```

## Output
- Hive tables: `<hiveDb>.sessions` (partitioned by job_run_date), `<hiveDb>.session_summary` (partitioned by job_run_date)
- Parquet: `/data/output/sessionization/` (partitioned by job_run_date)

Note: Each run adds job_run_id (UUID), created_at, updated_at, and job_run_date columns; tables are partitioned by job_run_date.