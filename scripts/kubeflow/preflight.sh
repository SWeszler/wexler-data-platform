#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
MIN_FREE_KIB=$((10 * 1024 * 1024))

render_with_retry() {
  local source=$1
  local output=$2
  local attempt
  for attempt in 1 2 3; do
    if kubectl kustomize "$source" > "${output}.tmp"; then
      mv "${output}.tmp" "$output"
      return 0
    fi
    echo "Kustomize render attempt $attempt failed for $source." >&2
  done
  return 1
}

free_kib=$(minikube ssh -- df -Pk /var | awk 'NR == 2 {print $4}')
if (( free_kib < MIN_FREE_KIB )); then
  echo "Minikube needs at least 10 GiB free; found $((free_kib / 1024 / 1024)) GiB." >&2
  exit 1
fi

minikube image ls | grep -q '^docker.io/library/log-analyzer-scala:latest$'
kubectl get deployment minio hive-metastore -n data >/dev/null
kubectl get deployment spark-operator-controller -n spark >/dev/null
kubectl wait --for=condition=Available deployment/minio deployment/hive-metastore -n data --timeout=30s
kubectl wait --for=condition=Available deployment/spark-operator-controller -n spark --timeout=30s

render_with_retry "$ROOT/k8s/kubeflow/app" /tmp/wexler-kfp-rendered.yaml
render_with_retry "$ROOT/k8s/kubeflow/cert-manager" /tmp/wexler-cert-manager-rendered.yaml
if grep -Eqi 'seaweedfs|kind: (VirtualService|AuthorizationPolicy)|name: (centraldashboard|dex|katib|kserve)' /tmp/wexler-kfp-rendered.yaml; then
  echo "Rendered KFP manifest contains an excluded component." >&2
  exit 1
fi

images=$(awk '$1 == "image:" {gsub(/"/, "", $2); print $2}' \
  /tmp/wexler-kfp-rendered.yaml /tmp/wexler-cert-manager-rendered.yaml | sort -u)
unsupported_images=0
while IFS= read -r image; do
  if ! docker manifest inspect --verbose "$image" | grep -q '"architecture": "arm64"'; then
    echo "Required image does not publish linux/arm64: $image" >&2
    unsupported_images=1
  fi
done <<< "$images"
if (( unsupported_images != 0 )); then
  exit 1
fi
if ! grep -q 'storage: 5Gi' /tmp/wexler-kfp-rendered.yaml; then
  echo "Rendered KFP manifest does not contain the 5 GiB MySQL PVC limit." >&2
  exit 1
fi

kubectl run kfp-minio-preflight --rm --restart=Never -n data \
  --image=minio/mc:RELEASE.2025-08-13T08-35-41Z \
  --env-from=secret/minio-root \
  --command -- /bin/sh -c \
  'mc alias set platform http://minio.data.svc.cluster.local:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc stat platform/logs/web_server_logs.txt >/dev/null'

echo "Kubeflow Pipelines preflight passed."
