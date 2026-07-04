# Repository Guidelines

## Project Structure & Module Organization

This repository defines a Kubernetes-based Spark, MinIO, Hive, and Trino data platform. The main orchestration is handled via Kubernetes manifests inside `k8s/` and spark applications in `jobs/`.

Spark examples and scripts live in `scripts/spark/`. The Scala log analysis job is in `jobs/log-analyzer-scala/`, with source code under `src/main/scala/`, SBT config in `build.sbt` and `project/`, and job-specific Spark/Hive config beside its Dockerfile. Challenge data and generator code live in `challenge/`.

## Build, Test, and Development Commands

- `make build-panel`: build the UI panel Docker image.
- `make load-panel`: load the UI panel image into Minikube.
- `make build-job`: build a Spark Scala job Docker image (e.g. `log-analyzer-scala`).
- `make load-job`: load the job image into Minikube.
- `kubectl apply -f k8s/platform/`: deploy the core platform services (MinIO, Hive, Trino, Spark History Server).

Use the UI Panel at `http://panel.wexler.test` (with `minikube tunnel` running) for monitoring and local inspection.

## Coding Style & Naming Conventions

Keep shell scripts POSIX/Bash-friendly, explicit, and readable. Use two-space indentation in YAML. Scala code should follow the current compact Spark style in `SessionAnalysis.scala`: clear DataFrame transformations, descriptive names, and minimal comments only where logic is non-obvious. Do not commit generated build output such as `target/`, `.bsp/`, `.scala-build/`, or IDE files.

## Testing Guidelines

There is no dedicated automated test suite. Validate changes by building affected images and running the smallest relevant job. For the Scala log analyzer, build the image, load it to Minikube, run the job on the cluster, and verify Spark finishes successfully plus expected Hive/Parquet outputs are produced.

For UI changes, verify the rendered interface in a browser before claiming the work is done. For fast local checks, create or reuse a Python virtualenv, install the UI requirements, run the app locally, and inspect it in a browser. When Kubernetes behavior or container packaging is part of the change, rebuild and redeploy the affected image, open the panel through port-forward or Ingress, and confirm the changed behavior in the browser.

## Commit & Pull Request Guidelines

Use short, imperative commit messages matching project history, for example `add log analysis script` or `update README with access instructions`. Pull requests should describe the service or job changed, include commands run for verification, and call out any Docker ports, HDFS paths, or Hive table changes.

## Agent-Specific Instructions

Prefer scoped edits. Do not revert unrelated user changes. Check live Docker state before making claims about running containers, ports, or Spark deployment behavior.
Do not claim a change is complete until the relevant test or browser verification has actually been run. If verification is blocked, say exactly what was not tested and why.
When adding a workaround, include a short explaining comment with the reason and the condition for removing it.
Do not elaborate in your replies; provide concise, actionable instructions. Avoid speculative or unverified statements about the environment.
