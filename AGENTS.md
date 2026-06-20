# Repository Guidelines

## Project Structure & Module Organization

This repository defines a Docker-based Hadoop, Spark, Hive, and Trino training environment. Core service images live in directories such as `base/`, `namenode/`, `datanode/`, `master/`, `worker/`, `historyserver/`, and `submit/`. The main orchestration file is `docker-compose.yml`, with shared Hadoop/Hive settings in `hadoop.env`, `hadoop-hive.env`, `conf/`, `spark/conf/`, and `trino/`.

Spark examples and scripts live in `scripts/spark/`. The Scala log analysis job is in `jobs/log-analyzer-scala/`, with source code under `src/main/scala/`, SBT config in `build.sbt` and `project/`, and job-specific Spark/Hive config beside its Dockerfile. Challenge data and generator code live in `challenge/`.

## Build, Test, and Development Commands

- `docker-compose up -d`: start the local Hadoop/Spark/Hive stack.
- `docker-compose down`: stop the stack.
- `docker build --platform linux/amd64 -t log-analyzer-scala .`: build the Scala log analyzer from `jobs/log-analyzer-scala/`.
- `docker run --rm --network wexler-data-platform_default ... log-analyzer-scala`: submit the log analyzer job.
- `make build`: build the base Hadoop service images.

Use Spark Master at `http://localhost:8080/`, Spark worker at `http://localhost:8081/`, and HDFS NameNode at `http://localhost:9870/` for local inspection.

## Coding Style & Naming Conventions

Keep shell scripts POSIX/Bash-friendly, explicit, and readable. Use two-space indentation in YAML and preserve existing Docker Compose style. Scala code should follow the current compact Spark style in `SessionAnalysis.scala`: clear DataFrame transformations, descriptive names, and minimal comments only where logic is non-obvious. Do not commit generated build output such as `target/`, `.bsp/`, `.scala-build/`, or IDE files.

## Testing Guidelines

There is no dedicated automated test suite. Validate changes by building affected images and running the smallest relevant job. For the Scala log analyzer, build the image, submit it against the Compose network, and verify Spark finishes successfully plus expected Hive/Parquet outputs are produced.

## Commit & Pull Request Guidelines

Use short, imperative commit messages matching project history, for example `add log analysis script` or `update README with access instructions`. Pull requests should describe the service or job changed, include commands run for verification, and call out any Docker ports, HDFS paths, or Hive table changes.

## Agent-Specific Instructions

Prefer scoped edits. Do not revert unrelated user changes. Check live Docker state before making claims about running containers, ports, or Spark deployment behavior.
When adding a workaround, include a short explaining comment with the reason and the condition for removing it.
Do not elaborate in your replies; provide concise, actionable instructions. Avoid speculative or unverified statements about the environment.
