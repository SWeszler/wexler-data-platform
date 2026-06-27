## Prerequisites

```bash
brew install minikube kubectl helm
```

Verify:
```bash
minikube version
kubectl version --client
helm version
```

---

## Phase 1 — Minikube

```bash
minikube start \
  --cpus=4 \
  --memory=11264 \
  --disk-size=60g \
  --driver=docker
```

```bash
kubectl get nodes
# Expected: one node, STATUS = Ready

minikube addons enable ingress
```

---

## Phase 2 — Spark Operator

```bash
kubectl create namespace spark

kubectl create serviceaccount spark -n spark

kubectl create clusterrolebinding spark-role \
  --clusterrole=edit \
  --serviceaccount=spark:spark \
  --namespace=spark
```

```bash
helm repo add spark-operator \
  https://kubeflow.github.io/spark-operator
helm repo update

helm install spark-operator spark-operator/spark-operator \
  --namespace spark \
  --version 2.5.1 \
  --set spark.jobNamespaces={spark} \
  --set webhook.enable=true

kubectl get pods -n spark --watch
# Ctrl+C once spark-operator-... is Running
```

Create `spark-pi.yaml`:

```yaml
apiVersion: sparkoperator.k8s.io/v1beta2
kind: SparkApplication
metadata:
  name: spark-pi
  namespace: spark
spec:
  type: Scala
  mode: cluster
  image: apache/spark:3.5.0
  imagePullPolicy: Always
  mainClass: org.apache.spark.examples.SparkPi
  mainApplicationFile: local:///opt/spark/examples/jars/spark-examples_2.12-3.5.0.jar
  sparkVersion: "3.5.0"
  restartPolicy:
    type: Never
  driver:
    cores: 1
    memory: "512m"
    serviceAccount: spark
  executor:
    cores: 1
    instances: 2
    memory: "512m"
```

```bash
kubectl apply -f ./k8s/spark-pi.yaml
```

```bash
kubectl get sparkapplication -n spark --watch
# Wait for STATUS = Completed
```

```bash
kubectl logs spark-pi-driver -n spark | grep "Pi is"
# Expected: Pi is roughly 3.14...
```

```bash
kubectl delete -f ./k8s/spark-pi.yaml
```

---

## Phase 3 — MinIO, Hive Metastore, HiveServer2, and Trino

Start the data/query services:

```bash
kubectl apply -f ./k8s/platform/namespaces.yaml
kubectl apply -f ./k8s/platform/minio.yaml
kubectl apply -f ./k8s/platform/hive.yaml
kubectl apply -f ./k8s/platform/trino.yaml
kubectl apply -f ./k8s/platform/spark-history.yaml

kubectl get pods -n data --watch
# Ctrl+C once minio, hive-metastore, hive-server, and trino are Running
```

Upload the log file through the MinIO Console:

```bash
kubectl port-forward -n data svc/minio 9001:9001
```

Open `http://localhost:9001`, sign in with `minioadmin` / `minioadmin`, create the `logs` bucket if needed, and upload `jobs/log-analyzer-scala/log-generator/web_server_logs.txt` as `web_server_logs.txt`.

The Spark job reads it from `s3a://logs/web_server_logs.txt`.

Build and load the Scala job image into minikube:

```bash
docker build --platform linux/arm64 -t log-analyzer-scala ./jobs/log-analyzer-scala
```

```bash
minikube image load log-analyzer-scala
```

Run the job through Spark Operator:

```bash
kubectl apply -f ./k8s/log-analyzer-scala.yaml
kubectl get sparkapplication -n spark --watch
kubectl logs -n spark log-analyzer-scala-driver
```

DataGrip connections:

```bash
kubectl port-forward deployment/hive-server --address localhost 10000:10000 -n data
kubectl port-forward -n data svc/trino 8089:8080
kubectl port-forward -n spark svc/spark-history-server 18080:18080
```

- Hive JDBC: `jdbc:hive2://localhost:10000/default;auth=noSasl`
- Trino JDBC: `jdbc:trino://localhost:8089/hive/default`
- MinIO console: `kubectl port-forward -n data svc/minio 9001:9001`, then open `http://localhost:9001`
- Spark History Server: `http://localhost:18080`

Hive DataGrip settings:

- Host: `localhost`
- Port: `10000`
- Database: `default`
- URL: `jdbc:hive2://localhost:10000/default;auth=noSasl`
- Authentication: no authentication / anonymous

If DataGrip starts its own Kubernetes port-forward, use the same target:

```bash
kubectl port-forward deployment/hive-server --address localhost 10000:10000 --namespace=data
```

If the port-forward connects and then immediately drops with `socat ... connect ... 127.0.0.1:10000: Connection refused`, HiveServer2 is not listening inside the pod. Check:

```bash
kubectl get pods -n data -l app=hive-server
kubectl logs -n data deploy/hive-server --tail=120
kubectl exec -n data deploy/hive-server -- \
  /opt/hive/bin/beeline -u 'jdbc:hive2://127.0.0.1:10000/default;auth=noSasl' -n hive -e 'show databases'
```

---

## Remaining migration work

The Kubernetes path now covers Spark Operator, MinIO object storage, Hive metastore, HiveServer2, Trino, and the Scala log analyzer. Remaining work before treating it as the default platform:

- Add PVCs or backup/restore notes for Hive metastore PostgreSQL and MinIO so cluster recreation does not lose metadata or object data unexpectedly.
- Update `jobs/log-analyzer-scala/README.md` to document the Kubernetes/S3A workflow and current defaults, because it still focuses on Docker/HDFS.
- Decide whether HiveServer2 and Trino should stay port-forward-only for local development or get NodePort/Ingress manifests for stable DataGrip endpoints.
- Pin and document the Spark Operator Helm repository/chart version used by the cluster, then keep `k8s/spark-pi.yaml` aligned with that Spark version.
- Decide whether legacy HDFS/YARN components are intentionally retired in Kubernetes or need replacement manifests. The current Kubernetes design is MinIO-first and does not deploy HDFS, YARN, NameNode, DataNode, ResourceManager, NodeManager, or Hadoop HistoryServer.

---

## Phase 4 — Kubeflow Pipelines

```bash
export PIPELINE_VERSION=2.16.1

kubectl apply -k \
  "github.com/kubeflow/pipelines/manifests/kustomize/cluster-scoped-resources?ref=${PIPELINE_VERSION}"

kubectl wait --for condition=established \
  --timeout=60s crd/applications.app.k8s.io

kubectl apply -k \
  "github.com/kubeflow/pipelines/manifests/kustomize/env/dev?ref=${PIPELINE_VERSION}"

kubectl get pods -n kubeflow --watch
# Ctrl+C when all pods are Running or Completed
```

```bash
kubectl port-forward svc/ml-pipeline-ui -n kubeflow 8080:80
```

Open `http://localhost:8080` — Kubeflow Pipelines UI.

---

## Handy commands

```bash
minikube stop        # pause without losing state
minikube start       # resume

minikube addons enable metrics-server
kubectl top nodes    # resource usage

kubectl port-forward spark-pi-driver 4040:4040 -n spark  # Spark UI

minikube delete      # nuke and start fresh
```
