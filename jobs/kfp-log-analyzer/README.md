# KFP Log Analyzer Smoke Pipeline

This pipeline compiles the existing
`jobs/log-analyzer-scala/sparkapplication.yaml` into a Kubernetes-native KFP
`Pipeline` and `PipelineVersion`. The launcher creates a uniquely named copy of
that SparkApplication and waits for Spark Operator to report completion.

Set up the SDK and compile the manifests:

```bash
make setup-kfp-sdk
make compile-kfp-pipeline
```

Build and load the launcher image:

```bash
make prepare-kfp-launcher
```

After KFP is installed, register the pipeline definitions:

```bash
kubectl apply -f jobs/kfp-log-analyzer/pipeline.yaml
```

The pipeline parameters are `timeout_minutes` (default `20`) and
`delete_after_success` (default `false`). Failed and timed-out SparkApplications
are retained for inspection.
