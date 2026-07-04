import os
from pathlib import Path
from typing import Any

import yaml

from models import SparkJob

ALLOWED_NAMESPACE = os.getenv("SPARK_JOBS_NAMESPACE", "spark")


def default_jobs_dir() -> Path:
    configured = os.getenv("SPARK_JOBS_DIR")
    if configured:
        return Path(configured)

    container_jobs = Path("/app/jobs")
    if container_jobs.exists():
        return container_jobs

    return Path(__file__).resolve().parents[2] / "jobs"


def discover_jobs(jobs_dir: Path | None = None) -> tuple[list[SparkJob], list[str]]:
    root = jobs_dir or default_jobs_dir()
    if not root.exists():
        return [], [f"Jobs directory not found: {root}"]

    jobs: list[SparkJob] = []
    errors: list[str] = []
    for manifest_path in sorted(root.glob("*/sparkapplication.yaml")):
        try:
            jobs.append(load_job(manifest_path))
        except ValueError as exc:
            errors.append(str(exc))

    return jobs, errors


def load_job(manifest_path: Path) -> SparkJob:
    try:
        manifest = yaml.safe_load(manifest_path.read_text()) or {}
    except yaml.YAMLError as exc:
        raise ValueError(f"{manifest_path}: invalid YAML: {exc}") from exc

    if not isinstance(manifest, dict):
        raise ValueError(f"{manifest_path}: manifest must be a YAML object")

    api_version = manifest.get("apiVersion")
    kind = manifest.get("kind")
    metadata = _mapping(manifest.get("metadata"))
    spec = _mapping(manifest.get("spec"))
    name = metadata.get("name")
    namespace = metadata.get("namespace")

    if api_version != "sparkoperator.k8s.io/v1beta2" or kind != "SparkApplication":
        raise ValueError(f"{manifest_path}: expected SparkApplication v1beta2")
    if not isinstance(name, str) or not name:
        raise ValueError(f"{manifest_path}: metadata.name is required")
    if not isinstance(namespace, str) or not namespace:
        raise ValueError(f"{manifest_path}: metadata.namespace is required")
    if namespace != ALLOWED_NAMESPACE:
        raise ValueError(
            f"{manifest_path}: metadata.namespace must be {ALLOWED_NAMESPACE}"
        )

    image = spec.get("image")
    return SparkJob(
        folder_name=manifest_path.parent.name,
        manifest_path=manifest_path,
        manifest=manifest,
        name=name,
        namespace=namespace,
        image=image if isinstance(image, str) else "",
    )


def _mapping(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}
