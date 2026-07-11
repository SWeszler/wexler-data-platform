# Active Roadmap

## Status Legend
⏳ Pending - Not started
🔄 In Progress - Currently being worked on
✅ Completed - Implemented and verified
🧪 Testing - Implementation complete, awaiting verification

---

## Immediate Tasks
### ⏳ Migrate to Gradle
Migrate the `log-analyzer-scala` project from SBT to Gradle to improve build times and simplify the configuration.
- **Convert Build Script**: Create a `build.gradle` file equivalent to the current `build.sbt`.
- **Apply Plugins**: Apply the Scala plugin and the Shadow plugin for creating fat jars (replacing `sbt-assembly`).
- **Dependencies**: Translate library dependencies to Gradle syntax.
- **Verification**: Ensure the project compiles and the fat jar can be built and submitted to Spark successfully.

### ⏳ Minikube Image Cleanup
Automate the cleanup of unused and dangling Docker images within the Minikube environment to reclaim disk space and optimize the local development cluster.
- **Identify Unused Images**: Determine which images inside Minikube are untagged (`<none>`) or not currently used by any running pods.
- **Automated Deletion**: Execute the necessary commands to safely remove these images (e.g., `docker image prune -a --filter "until=24h"` via `eval $(minikube docker-env)`).
- **Safety**: Ensure base images and images currently in use are not deleted.
- **Integration**: Create a `Makefile` target (e.g., `make clean-images`).

## Kubernetes Platform Remaining Work
- ✅ Add PVCs or backup/restore notes for Hive metastore PostgreSQL and MinIO so cluster recreation does not lose metadata or object data unexpectedly.
- ⏳ Decide the VM access method for HiveServer2 JDBC: port-forward over SSH/VPN, NodePort, LoadBalancer, or TCP ingress.
- ⏳ Add production VM hardening for Ingress: real DNS, TLS, authentication, and firewall/VPN restrictions.
- ⏳ Pin and document the Spark Operator Helm repository/chart version used by the cluster.


## Phase 4 — Kubeflow Pipelines
- ⏳ Install Kubeflow on the local `minikube` cluster using version 2.16.1.
- ⏳ Wrap Spark jobs as pipeline steps within a KFP DAG, giving them full scheduling, retry, and dependency logic.
- ⏳ Use Kubeflow's built-in dashboard in production and collect UX feedback before considering a custom UI.
