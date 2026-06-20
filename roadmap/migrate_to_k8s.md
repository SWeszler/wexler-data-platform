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
  --memory=8192 \
  --disk-size=30g \
  --driver=docker \
  --kubernetes-version=v1.27.0
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
  --set sparkJobNamespace=spark \
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
kubectl apply -f spark-pi.yaml

kubectl get sparkapplication -n spark --watch
# Wait for STATUS = Completed

kubectl logs spark-pi-driver -n spark | grep "Pi is"
# Expected: Pi is roughly 3.14...

kubectl delete -f spark-pi.yaml
```

---

## Phase 3 — Kubeflow Pipelines

```bash
kubectl apply -k \
  "github.com/kubeflow/pipelines/manifests/kustomize/cluster-scoped-resources?ref=2.0.0"

kubectl wait --for condition=established \
  --timeout=60s crd/applications.app.k8s.io

kubectl apply -k \
  "github.com/kubeflow/pipelines/manifests/kustomize/env/dev?ref=2.0.0"

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