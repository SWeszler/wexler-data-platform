JOB ?= log-analyzer-scala
DOCKER_PLATFORM ?= linux/arm64
PANEL_VERSION := $(shell cat ui/panel/VERSION)
PANEL_IMAGE ?= wexler-ui-panel:$(PANEL_VERSION)
EVENTSTORE_IMAGE ?= eventstore:local

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
