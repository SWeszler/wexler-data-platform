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
  https://googlecloudplatform.github.io/spark-on-k8s-operator
helm repo update

helm install spark-operator spark-operator/spark-operator \
  --namespace spark \
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

kubectl get pods -n data --watch
# Ctrl+C once minio, hive-metastore, hive-server, and trino are Running
```

Seed the log file into MinIO:

```bash
kubectl run minio-client -n data \
  --image=minio/mc \
  --restart=Never \
  --command -- sleep 3600

kubectl wait -n data --for=condition=Ready pod/minio-client --timeout=120s

kubectl exec -i -n data minio-client -- \
  sh -c 'cat > /tmp/web_server_logs.txt' \
  < ./jobs/log-analyzer-scala/log-generator/web_server_logs.txt

kubectl exec -n data minio-client -- \
  mc alias set local http://minio:9000 minioadmin minioadmin

kubectl exec -n data minio-client -- \
  mc mb -p local/logs local/warehouse

kubectl exec -n data minio-client -- \
  mc cp /tmp/web_server_logs.txt local/logs/web_server_logs.txt
```

Build and load the Scala job image into minikube:

```bash
docker build --platform linux/arm64 -t log-analyzer-scala:k8s ./jobs/log-analyzer-scala
```

```bash
minikube image load log-analyzer-scala:k8s
```

Run the job through Spark Operator:

```bash
kubectl apply -f ./k8s/log-analyzer-scala.yaml
kubectl get sparkapplication -n spark --watch
kubectl logs -n spark log-analyzer-scala-driver
```

DataGrip connections:

```bash
kubectl port-forward -n data svc/hive-server 10000:10000
kubectl port-forward -n data svc/trino 8089:8080
```

- Hive JDBC: `jdbc:hive2://localhost:10000`
- Trino JDBC: `jdbc:trino://localhost:8089/hive/default`
- MinIO console: `kubectl port-forward -n data svc/minio 9001:9001`, then open `http://localhost:9001`

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
