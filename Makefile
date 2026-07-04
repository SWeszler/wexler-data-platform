DOCKER_NETWORK = wexler-data-platform_default
ENV_FILE = hadoop.env
current_branch := $(shell git rev-parse --abbrev-ref HEAD)
JOB ?= log-analyzer-scala
DOCKER_PLATFORM ?= linux/arm64
PANEL_IMAGE ?= wexler-ui-panel:job-runner-rbac
build:
	docker build -t bde2020/hadoop-base:$(current_branch) ./base
	docker build -t bde2020/hadoop-namenode:$(current_branch) ./namenode
	docker build -t bde2020/hadoop-datanode:$(current_branch) ./datanode
	docker build -t bde2020/hadoop-resourcemanager:$(current_branch) ./resourcemanager
	docker build -t bde2020/hadoop-nodemanager:$(current_branch) ./nodemanager
	docker build -t bde2020/hadoop-historyserver:$(current_branch) ./historyserver
	docker build -t bde2020/hive:$(current_branch) ./

build-job:
	docker build --platform $(DOCKER_PLATFORM) -t $(JOB) ./jobs/$(JOB)

load-job:
	minikube image load $(JOB)

prepare-job: build-job load-job

build-panel:
	docker build --platform $(DOCKER_PLATFORM) -f ui/panel/Dockerfile -t $(PANEL_IMAGE) .

load-panel:
	minikube image load $(PANEL_IMAGE)
