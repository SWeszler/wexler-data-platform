import argparse
import json
import sys
import time
import uuid

from kubernetes import client, config
from kubernetes.client.exceptions import ApiException


GROUP = "sparkoperator.k8s.io"
VERSION = "v1beta2"
PLURAL = "sparkapplications"
NAMESPACE = "spark"
FAILURE_STATES = {"FAILED", "SUBMISSION_FAILED"}


def driver_logs(core_api: client.CoreV1Api, application: dict) -> str:
    pod_name = application.get("status", {}).get("driverInfo", {}).get("podName")
    if not pod_name:
        return "Spark driver pod was not reported."
    try:
        return core_api.read_namespaced_pod_log(
            pod_name, NAMESPACE, tail_lines=100
        )
    except ApiException as exc:
        return f"Unable to read Spark driver logs: {exc}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest-json", required=True)
    parser.add_argument("--timeout-minutes", required=True, type=int)
    parser.add_argument("--delete-after-success", required=True)
    parser.add_argument("--resource-name-output", required=True)
    args = parser.parse_args()

    manifest = json.loads(args.manifest_json)
    resource_name = f"log-analyzer-kfp-{uuid.uuid4().hex[:8]}"
    manifest["metadata"] = {
        "name": resource_name,
        "namespace": NAMESPACE,
        "labels": {"wexler.dev/managed-by": "kubeflow-pipelines"},
    }

    config.load_incluster_config()
    custom_api = client.CustomObjectsApi()
    core_api = client.CoreV1Api()
    custom_api.create_namespaced_custom_object(
        GROUP, VERSION, NAMESPACE, PLURAL, manifest
    )
    with open(args.resource_name_output, "w", encoding="utf-8") as output:
        output.write(resource_name)

    deadline = time.monotonic() + args.timeout_minutes * 60
    while time.monotonic() < deadline:
        application = custom_api.get_namespaced_custom_object(
            GROUP, VERSION, NAMESPACE, PLURAL, resource_name
        )
        state = (
            application.get("status", {})
            .get("applicationState", {})
            .get("state", "PENDING")
        )
        print(f"SparkApplication {resource_name}: {state}", flush=True)
        if state == "COMPLETED":
            if args.delete_after_success.lower() == "true":
                custom_api.delete_namespaced_custom_object(
                    GROUP, VERSION, NAMESPACE, PLURAL, resource_name
                )
            return 0
        if state in FAILURE_STATES:
            print(driver_logs(core_api, application), file=sys.stderr)
            return 1
        time.sleep(5)

    application = custom_api.get_namespaced_custom_object(
        GROUP, VERSION, NAMESPACE, PLURAL, resource_name
    )
    print(
        f"SparkApplication {resource_name} timed out after "
        f"{args.timeout_minutes} minutes.\n{driver_logs(core_api, application)}",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
