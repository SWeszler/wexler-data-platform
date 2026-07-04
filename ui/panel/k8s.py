import copy
import time
from typing import Any

from kubernetes import client, config
from kubernetes.client import ApiException

from models import SparkApplicationState, SparkJob

SPARK_GROUP = "sparkoperator.k8s.io"
SPARK_VERSION = "v1beta2"
SPARK_PLURAL = "sparkapplications"


def load_config() -> None:
    try:
        config.load_incluster_config()
    except config.ConfigException:
        config.load_kube_config()


def get_clients() -> tuple[client.CustomObjectsApi, client.CoreV1Api]:
    load_config()
    return client.CustomObjectsApi(), client.CoreV1Api()


def get_application(job: SparkJob) -> dict[str, Any] | None:
    custom_api, _ = get_clients()
    try:
        return custom_api.get_namespaced_custom_object(
            SPARK_GROUP, SPARK_VERSION, job.namespace, SPARK_PLURAL, job.name
        )
    except ApiException as exc:
        if exc.status == 404:
            return None
        raise


def create_application(job: SparkJob) -> None:
    custom_api, _ = get_clients()
    custom_api.create_namespaced_custom_object(
        SPARK_GROUP, SPARK_VERSION, job.namespace, SPARK_PLURAL, job.manifest
    )


def delete_application(job: SparkJob) -> None:
    custom_api, _ = get_clients()
    try:
        custom_api.delete_namespaced_custom_object(
            SPARK_GROUP, SPARK_VERSION, job.namespace, SPARK_PLURAL, job.name
        )
    except ApiException as exc:
        if exc.status != 404:
            raise


def rerun_application(job: SparkJob) -> None:
    delete_debug_services(job)
    delete_application(job)
    wait_for_application_deleted(job)
    create_application(job)


def wait_for_application_deleted(job: SparkJob, timeout_seconds: int = 30) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if get_application(job) is None:
            return
        time.sleep(1)
    raise TimeoutError(f"Timed out waiting for {job.name} to be deleted")


def application_state(job: SparkJob) -> SparkApplicationState:
    app = get_application(job)
    if not app:
        return SparkApplicationState(False, "Not submitted", None, None)

    metadata = app.get("metadata") if isinstance(app.get("metadata"), dict) else {}
    labels = metadata.get("labels") if isinstance(metadata.get("labels"), dict) else {}
    debug_mode = labels.get("wexler.dev/debug-mode") == "true"

    status = app.get("status") if isinstance(app.get("status"), dict) else {}
    phase = status.get("applicationState", {}).get("state")
    driver_info = status.get("driverInfo") if isinstance(status.get("driverInfo"), dict) else {}
    driver_pod = driver_info.get("podName")

    return SparkApplicationState(
        exists=True,
        phase=phase if isinstance(phase, str) else "Submitted",
        driver_pod=driver_pod if isinstance(driver_pod, str) else f"{job.name}-driver",
        ui_service=expected_ui_service(job),
        debug_mode=debug_mode,
    )


def service_exists(namespace: str, name: str) -> bool:
    _, core_api = get_clients()
    try:
        core_api.read_namespaced_service(name, namespace)
    except ApiException as exc:
        if exc.status == 404:
            return False
        raise
    return True


def driver_logs(job: SparkJob, driver_pod: str | None, tail_lines: int = 200) -> str:
    if not driver_pod:
        return "Driver pod is not known yet."

    _, core_api = get_clients()
    return core_api.read_namespaced_pod_log(
        name=driver_pod,
        namespace=job.namespace,
        tail_lines=tail_lines,
        timestamps=True,
    )


def expected_ui_service(job: SparkJob) -> str:
    return f"{job.name}-ui-svc"


# ---------------------------------------------------------------------------
# Debug mode
# ---------------------------------------------------------------------------

DRIVER_DEBUG_PORT = 5005
EXECUTOR_DEBUG_PORT = 5006
_JDWP_DRIVER = (
    f"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:{DRIVER_DEBUG_PORT}"
)
_JDWP_EXECUTOR = (
    f"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:{EXECUTOR_DEBUG_PORT}"
)


def debug_manifest(job: SparkJob) -> dict[str, Any]:
    """Deep-copy the job manifest and inject JDWP agent options."""
    manifest = copy.deepcopy(job.manifest)

    metadata = manifest.setdefault("metadata", {})
    metadata.setdefault("labels", {})["wexler.dev/debug-mode"] = "true"
    metadata.setdefault("annotations", {})["wexler.dev/debug-mode"] = "true"

    spec = manifest.setdefault("spec", {})

    driver = spec.setdefault("driver", {})
    existing_driver_opts = driver.get("javaOptions", "")
    driver["javaOptions"] = f"{existing_driver_opts} {_JDWP_DRIVER}".strip()

    executor = spec.setdefault("executor", {})
    existing_executor_opts = executor.get("javaOptions", "")
    executor["javaOptions"] = f"{existing_executor_opts} {_JDWP_EXECUTOR}".strip()
    # Force single executor so the debug service selector is deterministic.
    executor["instances"] = 1

    return manifest


def _debug_service_body(
    job: SparkJob, role: str, target_port: int
) -> dict[str, Any]:
    """Build a LoadBalancer Service targeting a Spark pod by role."""
    return {
        "apiVersion": "v1",
        "kind": "Service",
        "metadata": {
            "name": f"{job.name}-{role}-debug",
            "namespace": job.namespace,
            "labels": {
                "wexler.dev/debug-mode": "true",
                "wexler.dev/debug-for": job.name,
            },
        },
        "spec": {
            "type": "LoadBalancer",
            "selector": {
                "sparkoperator.k8s.io/app-name": job.name,
                "spark-role": role,
            },
            "ports": [
                {
                    "name": "jdwp",
                    "port": target_port,
                    "targetPort": target_port,
                    "protocol": "TCP",
                }
            ],
        },
    }


def create_debug_services(job: SparkJob) -> None:
    """Create LoadBalancer debug services for driver and executor."""
    _, core_api = get_clients()
    delete_debug_services(job)
    wait_for_debug_services_deleted(job)
    for role, port in [("driver", DRIVER_DEBUG_PORT), ("executor", EXECUTOR_DEBUG_PORT)]:
        body = _debug_service_body(job, role, port)
        core_api.create_namespaced_service(job.namespace, body)


def delete_debug_services(job: SparkJob) -> None:
    """Remove debug LoadBalancer services for a job, if they exist."""
    _, core_api = get_clients()
    for role in ("driver", "executor"):
        svc_name = f"{job.name}-{role}-debug"
        try:
            core_api.delete_namespaced_service(svc_name, job.namespace)
        except ApiException as exc:
            if exc.status != 404:
                raise


def wait_for_debug_services_deleted(job: SparkJob, timeout_seconds: int = 30) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        service_names = [f"{job.name}-{role}-debug" for role in ("driver", "executor")]
        if all(not service_exists(job.namespace, service_name) for service_name in service_names):
            return
        time.sleep(1)
    raise TimeoutError(f"Timed out waiting for {job.name} debug services to be deleted")


def debug_run_application(job: SparkJob) -> None:
    """Delete existing app + debug services, then submit debug version."""
    delete_application(job)
    wait_for_application_deleted(job)
    create_debug_services(job)
    custom_api, _ = get_clients()
    custom_api.create_namespaced_custom_object(
        SPARK_GROUP, SPARK_VERSION, job.namespace, SPARK_PLURAL, debug_manifest(job)
    )
