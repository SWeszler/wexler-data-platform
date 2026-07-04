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

    status = app.get("status") if isinstance(app.get("status"), dict) else {}
    phase = status.get("applicationState", {}).get("state")
    driver_info = status.get("driverInfo") if isinstance(status.get("driverInfo"), dict) else {}
    driver_pod = driver_info.get("podName")

    return SparkApplicationState(
        exists=True,
        phase=phase if isinstance(phase, str) else "Submitted",
        driver_pod=driver_pod if isinstance(driver_pod, str) else f"{job.name}-driver",
        ui_service=expected_ui_service(job),
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
