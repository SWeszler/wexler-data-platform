import json
import os
from dataclasses import dataclass
from typing import Any

import streamlit as st


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
        "command": "kubectl port-forward deployment/hive-server --address localhost 10000:10000 -n data",
    },
    {
        "name": "Trino",
        "value": f"jdbc:trino://{HOST}:{TRINO_PORT}/hive/default",
        "command": "kubectl port-forward -n data svc/trino 8089:8080",
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
st.write("Use these URLs after starting the Hive and Trino JDBC port-forwards.")

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
st.write("Current Kubernetes SparkApplication names and useful commands.")
st.code(
    "\n".join(
        [
            "kubectl get sparkapplication -n spark",
            "kubectl get pods -n spark",
            "kubectl logs -n spark log-analyzer-scala-driver",
        ]
    ),
    language="bash",
)

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
