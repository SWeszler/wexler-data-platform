# Infrastructure Modernization Brief

## Current State & Challenges
The current local infrastructure relies on the `bde2020` (Big Data Europe) Docker images. While excellent for local training and learning, this ecosystem is showing its age.

**Key Issues with Current Stack:**
1. **Outdated Software:** Relying on Hadoop 3.2.1, Spark 3.0.0, and Java 8. These versions lack modern features, performance improvements, and syntax enhancements.
2. **Security Vulnerabilities:** Because the `bde2020` project is no longer actively maintained, the images contain unpatched CVEs (such as Log4Shell) and run on outdated Linux base images.
3. **Orchestration:** Docker Compose is great for local prototyping, but lacks the self-healing, scaling, and resource management capabilities required for a modern production big data cluster.
4. **No Job Scheduling:** The current stack has no native scheduler. Jobs can only be triggered manually via `spark-submit`. There is no concept of cron schedules, retries, or cross-job dependencies.

## Modernization Goals
The goal is to transition the cluster to a modern, secure, and actively maintained ecosystem — ultimately running on **Kubernetes** with **Kubeflow** as the orchestration and ML platform layer.

---

### Phase 1: Image & Version Upgrades (Local Docker)
> *Stabilize the current stack before migrating.*

* **Replace `bde2020` Images:** Transition away from the abandoned `bde2020` repository.
  * **Spark:** Move to the actively maintained **Bitnami Apache Spark images** (`bitnami/spark:3.5.x`). Bitnami provides secure, constantly updated, and production-ready containers.
  * **Hadoop:** Move to the official Apache Hadoop images or build custom, minimal Dockerfiles based on the latest Hadoop releases (e.g., `3.3.x`).
* **Runtime Upgrade:** Upgrade the underlying Java runtime from Java 8 to **Java 11 or Java 17**, ensuring better garbage collection and performance.
* **Component Upgrades:**
  * Upgrade Spark to `3.4.x` or `3.5.x`.
  * Upgrade Presto to **Trino** (the modern, more active fork of Presto).

---

### Phase 2: Storage & Format Modernization
> *Decouple compute and storage before the Kubernetes migration.*

* **Cloud Storage Simulation:** Introduce a local S3-compatible object storage layer (like **MinIO**) to replace or complement HDFS. Modern architectures favor decoupled compute (Spark) and storage (S3) over tightly coupled HDFS.
* **Table Formats:** Introduce modern open table formats like **Apache Iceberg** or **Delta Lake** into the Spark and Trino processing pipelines, replacing raw Parquet/Hive tables.

---

### Phase 3: Kubernetes Migration
> *Migrate from Docker Compose to a production-grade Kubernetes environment.*

* **Local Kubernetes Cluster:** Set up a local K8s cluster using **minikube** or Docker Desktop's built-in Kubernetes to simulate a true production environment.
* **Helm-based Deployments:** Replace `docker-compose.yml` service definitions with official **Helm Charts** for all components:
  * `bitnami/spark` Helm chart for Spark Master + Workers
  * `bitnami/trino` Helm chart for the query engine
  * `bitnami/minio` Helm chart for object storage
* **Spark on Kubernetes:** Transition from Spark Standalone / YARN to Spark's **native Kubernetes scheduler** (`--master k8s://...`), enabling dynamic executor scaling.
* **Service Mesh (Optional):** Introduce a lightweight service mesh like **Istio** or **Linkerd** for traffic management, mTLS, and observability between services.

---

### Phase 4: Kubeflow for Orchestration & ML Pipelines
> *Replace the need for a separate Airflow instance by adopting Kubeflow — Kubernetes' native ML & pipeline orchestration platform.*

* **Why Kubeflow over Airflow?**
  * Kubeflow is purpose-built for Kubernetes and requires no separate infrastructure — it runs *inside* the K8s cluster we are already migrating to in Phase 3.
  * **Kubeflow Pipelines (KFP)** provides DAG-based workflow orchestration with cron scheduling, retries, and dependency management — everything Airflow offers, but natively K8s-native.
  * It adds first-class support for ML-specific steps: distributed training (via **TFJob**, **PyTorchJob**), hyperparameter tuning (**Katib**), and model serving (**KServe**) — capabilities that Airflow requires third-party operators to replicate.
  * Pipelines are defined in Python using the **KFP SDK**, which compiles them to Argo Workflow YAML specs running inside the cluster.
* **Kubeflow Components to Deploy:**

  | Component | Purpose |
  |---|---|
  | **Kubeflow Pipelines** | DAG authoring, cron scheduling, monitoring |
  | **Katib** | Automated hyperparameter tuning |
  | **KServe** | Model serving & inference endpoints |
  | **Kubeflow Dashboard** | Unified UI for all ML/pipeline operations |

* **Pipeline Integration:** Spark jobs currently submitted via `spark-submit` will be wrapped as pipeline steps within a KFP DAG, giving them full scheduling, retry, and dependency logic.

---

### Phase 5: UI Strategy — Evaluate First, Build Only If Needed
> *Kubeflow ships with a capable built-in dashboard. A custom UI should only be built if concrete gaps are identified.*

**Kubeflow's built-in dashboard already provides:**
* Visual DAG graph for every pipeline run
* Run history, logs, and artifact lineage
* Cron-based recurring run scheduling
* Experiment tracking and metrics
* Notebook server management (JupyterHub)
* Katib tuning experiments and KServe model endpoints

**Build a custom UI layer only if one or more of these gaps emerge:**

| Gap | When it matters |
|---|---|
| Non-technical end users need a simplified interface | Business analysts or ops teams who shouldn't see raw pipeline internals |
| Custom branding or white-labeling is required | Internal portals or client-facing products |
| You need to unify Kubeflow with other internal tools | e.g., linking pipelines to a custom job catalog or data catalog |
| Kubeflow's UX becomes a blocker to daily workflows | Identified through actual usage after Phase 4 |

**Recommended approach:**
1. **Use Kubeflow's dashboard as-is** after Phase 4 deployment.
2. **Collect feedback** from actual users over several weeks of real usage.
3. **Build a thin custom layer** (if needed) using **React/Next.js** that calls the **Kubeflow Pipelines REST API** — only for the specific views or workflows where Kubeflow falls short.

> **Decision point:** Phase 5 is deferred until after Phase 4 is running in production. The build/no-build decision should be driven by real usage gaps, not assumptions.

---

## Migration Path Summary

```
Current State                    Target State
─────────────────────────────    ─────────────────────────────────────────
Docker Compose             →     Kubernetes (minikube / cloud)
bde2020 images             →     Bitnami / Official images
Spark Standalone / YARN    →     Spark on Kubernetes (native scheduler)
HDFS                       →     MinIO (S3-compatible object storage)
Hive Tables (raw Parquet)  →     Apache Iceberg / Delta Lake
No scheduler               →     Kubeflow Pipelines (DAGs + cron)
Manual spark-submit        →     Kubeflow Pipeline steps
No UI                      →     Kubeflow Dashboard (+ custom layer if needed)
```

---

## Next Steps
1. Create a branch to test swapping the `bde2020/spark-master` and `spark-worker` with `bitnami/spark` in `docker-compose.yml`.
2. Validate that the current Scala/Python job templates still successfully submit to the new Spark version.
3. Set up a local `minikube` cluster and deploy Spark via its Helm chart as a proof of concept.
4. Install Kubeflow on the local `minikube` cluster and author a sample pipeline using the KFP Python SDK.
5. Use Kubeflow's built-in dashboard in production and collect UX feedback to inform the Phase 5 build/no-build decision.
