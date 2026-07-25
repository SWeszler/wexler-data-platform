import json
from pathlib import Path

import yaml
from kfp import compiler, dsl
from kfp.compiler import KubernetesManifestOptions


ROOT = Path(__file__).resolve().parents[2]
SPARK_MANIFEST = ROOT / "jobs/log-analyzer-scala/sparkapplication.yaml"
OUTPUT_MANIFEST = Path(__file__).with_name("pipeline.yaml")
LAUNCHER_IMAGE = "kfp-spark-launcher:0.1.0"


def load_spark_manifest() -> str:
    with SPARK_MANIFEST.open(encoding="utf-8") as stream:
        manifest = yaml.safe_load(stream)
    if manifest.get("apiVersion") != "sparkoperator.k8s.io/v1beta2":
        raise ValueError("Expected a sparkoperator.k8s.io/v1beta2 manifest")
    if manifest.get("kind") != "SparkApplication":
        raise ValueError("Expected a SparkApplication manifest")
    if manifest.get("metadata", {}).get("name") != "log-analyzer-scala":
        raise ValueError("Expected the log-analyzer-scala SparkApplication")
    manifest.pop("status", None)
    return json.dumps(manifest, separators=(",", ":"))


@dsl.container_component
def launch_log_analyzer(
    manifest_json: str,
    timeout_minutes: int,
    delete_after_success: bool,
    resource_name: dsl.OutputPath(str),
):
    return dsl.ContainerSpec(
        image=LAUNCHER_IMAGE,
        command=["python", "/app/launcher.py"],
        args=[
            "--manifest-json",
            manifest_json,
            "--timeout-minutes",
            timeout_minutes,
            "--delete-after-success",
            delete_after_success,
            "--resource-name-output",
            resource_name,
        ],
    )


@dsl.pipeline(
    name="log-analyzer-spark-smoke",
    description="Run the repository log analyzer through Spark Operator.",
)
def log_analyzer_pipeline(
    timeout_minutes: int = 20,
    delete_after_success: bool = False,
):
    launch_log_analyzer(
        manifest_json=load_spark_manifest(),
        timeout_minutes=timeout_minutes,
        delete_after_success=delete_after_success,
    ).set_caching_options(False)


if __name__ == "__main__":
    options = KubernetesManifestOptions(
        pipeline_name="log-analyzer-spark-smoke",
        pipeline_display_name="Log Analyzer Spark Smoke",
        pipeline_version_name="log-analyzer-spark-smoke-v1",
        pipeline_version_display_name="Log Analyzer Spark Smoke v1",
        namespace="kubeflow",
        include_pipeline_manifest=True,
    )
    compiler.Compiler().compile(
        pipeline_func=log_analyzer_pipeline,
        package_path=str(OUTPUT_MANIFEST),
        kubernetes_manifest_options=options,
        kubernetes_manifest_format=True,
    )
