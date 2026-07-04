import json
import os
from dataclasses import dataclass
from typing import Any

import streamlit as st
from kubernetes.client import ApiException

import k8s
from jobs import default_jobs_dir, discover_jobs
from models import SparkApplicationState, SparkJob


def env(name: str, default: str) -> str:
    return os.getenv(name, default)


HOST = env("PUBLIC_HOST", "localhost")
PANEL_PORT = env("PANEL_PORT", "8501")
MINIO_PORT = env("MINIO_CONSOLE_PORT", "9001")
SPARK_HISTORY_PORT = env("SPARK_HISTORY_PORT", "18080")
TRINO_PORT = env("TRINO_PORT", "8089")
HIVE_PORT = env("HIVE_PORT", "10000")
SPARK_UI_PORT = env("SPARK_UI_PORT", "4040")
MINIO_INGRESS_URL = env("MINIO_INGRESS_URL", "http://minio.wexler.test")
TRINO_INGRESS_URL = env("TRINO_INGRESS_URL", "http://trino.wexler.test/ui/")
SPARK_HISTORY_INGRESS_URL = env(
    "SPARK_HISTORY_INGRESS_URL", "http://spark-history.wexler.test"
)
PANEL_INGRESS_URL = env("PANEL_INGRESS_URL", "http://panel.wexler.test")
SPARK_JOBS_DIR = default_jobs_dir()


@dataclass(frozen=True)
class ServiceLink:
    name: str
    url: str
    description: str


@dataclass(frozen=True)
class CodeLink:
    name: str
    value: str
    command: str


@dataclass(frozen=True)
class PathItem:
    name: str
    value: str


def localhost_url(port: str, path: str = "") -> str:
    suffix = path if path.startswith("/") or not path else f"/{path}"
    return f"http://{HOST}:{port}{suffix}"


def load_json_list(name: str, default: list[dict[str, Any]]) -> list[dict[str, Any]]:
    raw = os.getenv(name)
    if not raw:
        return default
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError:
        return default
    if not isinstance(parsed, list):
        return default
    return [item for item in parsed if isinstance(item, dict)]


def service_links(name: str, default: list[dict[str, str]]) -> list[ServiceLink]:
    return [
        ServiceLink(
            item.get("name", "Unnamed"),
            item.get("url", "#"),
            item.get("description", ""),
        )
        for item in load_json_list(name, default)
    ]


def code_links(name: str, default: list[dict[str, str]]) -> list[CodeLink]:
    return [
        CodeLink(
            item.get("name", "Unnamed"),
            item.get("value", ""),
            item.get("command", ""),
        )
        for item in load_json_list(name, default)
    ]


def path_items(name: str, default: list[dict[str, str]]) -> list[PathItem]:
    return [
        PathItem(
            item.get("name", "Unnamed"),
            item.get("value", ""),
        )
        for item in load_json_list(name, default)
    ]


def is_running(state: SparkApplicationState) -> bool:
    return state.phase.lower() in {
        "submitted",
        "pending",
        "running",
        "succeeding",
        "unknown",
    }


def is_completed(state: SparkApplicationState) -> bool:
    return state.phase.lower() in {"completed", "succeeded", "failed", "failedsubmission"}


def run_action(label: str, job: SparkJob) -> None:
    try:
        if label == "Run":
            k8s.create_application(job)
        elif label == "Rerun":
            k8s.rerun_application(job)
        elif label == "Delete":
            k8s.delete_application(job)
        st.success(f"{label} requested for {job.name}.")
        st.rerun()
    except ApiException as exc:
        st.error(f"Kubernetes API error: {exc.status} {exc.reason}")
    except Exception as exc:
        st.error(f"Unable to reach Kubernetes: {exc}")


def render_spark_ui(job: SparkJob, state: SparkApplicationState) -> None:
    service_name = state.ui_service or k8s.expected_ui_service(job)
    if is_running(state):
        try:
            service_found = k8s.service_exists(job.namespace, service_name)
        except Exception:
            service_found = False
        if service_found:
            st.caption(f"Driver UI service detected: {service_name}")
        else:
            st.caption(f"Expected driver UI service: {service_name}")
        st.link_button("Open Spark UI", localhost_url(SPARK_UI_PORT), use_container_width=True)
        st.code(
            f"kubectl port-forward -n {job.namespace} svc/{service_name} {SPARK_UI_PORT}:4040",
            language="bash",
        )
    elif is_completed(state):
        st.link_button(
            "Open History Server", SPARK_HISTORY_INGRESS_URL, use_container_width=True
        )


def render_logs(job: SparkJob, state: SparkApplicationState) -> None:
    open_key = f"logs-open-{job.name}"
    button_key = f"logs-button-{job.name}"
    if st.button("Inspect logs", key=button_key, use_container_width=True):
        st.session_state[open_key] = True
    if st.session_state.get(open_key):
        try:
            st.code(k8s.driver_logs(job, state.driver_pod), language="text")
        except ApiException as exc:
            st.error(f"Kubernetes API error: {exc.status} {exc.reason}")
        except Exception as exc:
            st.error(f"Unable to read logs: {exc}")


def render_job_card(job: SparkJob) -> None:
    with st.container(border=True):
        title, status_col = st.columns([2, 1])
        title.subheader(job.folder_name)
        try:
            state = k8s.application_state(job)
            status_col.metric("Status", state.phase)
        except ApiException as exc:
            state = SparkApplicationState(False, "Unavailable", None, None)
            status_col.metric("Status", "Unavailable")
            st.warning(f"Kubernetes API error: {exc.status} {exc.reason}")
        except Exception as exc:
            state = SparkApplicationState(False, "Unavailable", None, None)
            status_col.metric("Status", "Unavailable")
            st.warning(f"Kubernetes is not reachable from this panel: {exc}")

        st.caption(f"{job.name} in namespace {job.namespace}")
        if job.image:
            st.code(job.image, language="text")

        run_col, rerun_col, delete_col = st.columns(3)
        if run_col.button("Run", key=f"run-{job.name}", disabled=state.exists):
            run_action("Run", job)
        if rerun_col.button("Rerun", key=f"rerun-{job.name}"):
            run_action("Rerun", job)
        if delete_col.button("Delete", key=f"delete-{job.name}", disabled=not state.exists):
            run_action("Delete", job)

        render_spark_ui(job, state)
        render_logs(job, state)


default_browser_links = [
    {
        "name": "MinIO Console",
        "url": MINIO_INGRESS_URL,
        "description": "Browse buckets and upload input files.",
    },
    {
        "name": "Trino UI",
        "url": TRINO_INGRESS_URL,
        "description": "Inspect Trino coordinator and query activity.",
    },
    {
        "name": "Spark History Server",
        "url": SPARK_HISTORY_INGRESS_URL,
        "description": "View completed Spark applications from MinIO event logs.",
    },
    {
        "name": "UI Panel",
        "url": PANEL_INGRESS_URL,
        "description": "Open this launcher through Ingress.",
    },
]

default_datagrip_links = [
    {
        "name": "Hive",
        "value": f"jdbc:hive2://{HOST}:{HIVE_PORT}/default;auth=noSasl",
        "command": "Requires k8s/access/local-jdbc.yaml and a LoadBalancer/tunnel provider",
    },
    {
        "name": "Trino",
        "value": f"jdbc:trino://{HOST}:18089/hive/default",
        "command": "Requires k8s/access/local-jdbc.yaml and a LoadBalancer/tunnel provider",
    },
]

default_s3_paths = [
    {"name": "Input logs", "value": "s3a://logs/web_server_logs.txt"},
    {"name": "Spark warehouse", "value": "s3a://warehouse/spark-warehouse"},
    {"name": "Hive warehouse", "value": "s3a://warehouse/hive"},
    {"name": "Spark event logs", "value": "s3a://warehouse/spark-events"},
    {"name": "Log analyzer output", "value": "s3a://warehouse/output/sessionization"},
]

ingress_links = service_links("BROWSER_LINKS_JSON", default_browser_links)
datagrip_links = code_links("DATAGRIP_LINKS_JSON", default_datagrip_links)
s3_paths = path_items("S3_PATHS_JSON", default_s3_paths)

st.set_page_config(
    page_title="Wexler Data Platform",
    layout="wide",
)

st.title("Wexler Data Platform")
st.caption("Local Kubernetes links for Spark, MinIO, Hive, and Trino")

st.info(
    "Browser links use Ingress. Keep sudo minikube tunnel running and map the "
    "wexler.test hostnames to 127.0.0.1 in /etc/hosts."
)

st.header("Browser UIs")
cols = st.columns(2)
for index, link in enumerate(ingress_links):
    with cols[index % 2]:
        st.subheader(link.name)
        st.write(link.description)
        st.link_button(f"Open {link.name}", link.url, use_container_width=True)
        st.code(link.url, language="text")

st.header("DataGrip")
st.write("Use these URLs after applying local JDBC access services and starting a LoadBalancer/tunnel provider.")

datagrip_cols = st.columns(2)
for index, item in enumerate(datagrip_links):
    with datagrip_cols[index % 2]:
        st.subheader(item.name)
        st.code(item.value, language="text")
        if item.command:
            st.code(item.command, language="bash")

st.header("S3 / MinIO Paths")
st.write("Current object paths used by the Spark job and platform services.")

for item in s3_paths:
    left, right = st.columns([1, 3])
    left.write(item.name)
    right.code(item.value, language="text")

st.header("Spark Jobs")
st.write("Run SparkApplication manifests discovered from job folders.")
st.caption(f"Jobs directory: {SPARK_JOBS_DIR}")

@st.fragment(run_every="5s")
def render_spark_jobs():
    spark_jobs, spark_job_errors = discover_jobs(SPARK_JOBS_DIR)
    if spark_job_errors:
        for error in spark_job_errors:
            st.warning(error)
    if not spark_jobs:
        st.info("No runnable jobs found. Add jobs/<name>/sparkapplication.yaml.")
    for spark_job in spark_jobs:
        render_job_card(spark_job)

render_spark_jobs()

st.header("Ingress Setup")
st.code("sudo minikube tunnel", language="bash")
st.code(
    "\n".join(
        [
            "127.0.0.1 panel.wexler.test",
            "127.0.0.1 minio.wexler.test",
            "127.0.0.1 trino.wexler.test",
            "127.0.0.1 spark-history.wexler.test",
        ]
    ),
    language="text",
)
