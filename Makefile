JOB ?= log-analyzer-scala
DOCKER_PLATFORM ?= linux/arm64
PANEL_VERSION := $(shell cat ui/panel/VERSION)
PANEL_IMAGE ?= wexler-ui-panel:$(PANEL_VERSION)
EVENTSTORE_IMAGE ?= eventstore:local
KFP_LAUNCHER_IMAGE ?= kfp-spark-launcher:0.1.0
KFP_PYTHON ?= .venv-kfp/bin/python

build-job:
	docker build --platform $(DOCKER_PLATFORM) -t $(JOB) ./jobs/$(JOB)

load-job:
	minikube image load $(JOB)

prepare-job: build-job load-job

build-panel:
	docker build --platform $(DOCKER_PLATFORM) -f ui/panel/Dockerfile -t $(PANEL_IMAGE) .

load-panel:
	minikube image load $(PANEL_IMAGE)

prepare-panel: build-panel load-panel

build-eventstore:
	docker build --platform $(DOCKER_PLATFORM) -t $(EVENTSTORE_IMAGE) ./services/eventstore

load-eventstore:
	minikube image load $(EVENTSTORE_IMAGE)

prepare-eventstore: build-eventstore load-eventstore

build-kfp-launcher:
	docker build --platform $(DOCKER_PLATFORM) -t $(KFP_LAUNCHER_IMAGE) ./jobs/kfp-log-analyzer

load-kfp-launcher:
	minikube image load $(KFP_LAUNCHER_IMAGE)

prepare-kfp-launcher: build-kfp-launcher load-kfp-launcher

setup-kfp-sdk:
	python3 -m venv .venv-kfp
	.venv-kfp/bin/pip install -r ./jobs/kfp-log-analyzer/requirements.txt

compile-kfp-pipeline:
	$(KFP_PYTHON) ./jobs/kfp-log-analyzer/pipeline.py

render-kubeflow:
	kubectl kustomize ./k8s/kubeflow/cluster-scoped
	kubectl kustomize ./k8s/kubeflow/cert-manager
	kubectl kustomize ./k8s/kubeflow/app

preflight-kubeflow:
	./scripts/kubeflow/preflight.sh

install-kubeflow:
	kubectl apply -k ./k8s/kubeflow/cluster-scoped
	kubectl wait --for=condition=established --timeout=60s crd/applications.app.k8s.io
	kubectl apply -k ./k8s/kubeflow/cert-manager
	kubectl wait --for=condition=Available deployment/cert-manager-webhook -n cert-manager --timeout=300s
	kubectl apply -k ./k8s/kubeflow/app

status-kubeflow:
	kubectl get pods,pvc,ingress -n kubeflow
	kubectl get pipelines,pipelineversions -n kubeflow

uninstall-kubeflow:
	kubectl delete -k ./k8s/kubeflow/app --ignore-not-found

update-jobs-catalog:
	@echo "Updating spark-jobs-catalog ConfigMap..."
	@CM_ARGS=""; \
	for manifest in jobs/*/sparkapplication.yaml; do \
		if [ -f "$$manifest" ]; then \
			job_dir=$$(basename $$(dirname "$$manifest")); \
			CM_ARGS="$$CM_ARGS --from-file=$$job_dir.yaml=$$manifest"; \
		fi \
	done; \
	kubectl create configmap spark-jobs-catalog --namespace platform $$CM_ARGS -o yaml --dry-run=client | kubectl apply -f -
