# Spark Job Onboarding

The UI panel discovers runnable Spark jobs from folders under `jobs/`. A folder is runnable when it contains `sparkapplication.yaml`.

## Add a job

1. Create `jobs/<name>/`.
2. Add a `Dockerfile` that builds the Spark driver image.
3. Add `jobs/<name>/sparkapplication.yaml`.
4. Build and load the job image:

   ```bash
   make prepare-job JOB=<name>
   ```

5. Rebuild and load the panel image because job manifests are copied into it:

   ```bash
   make build-panel
   make load-panel
   ```

6. Redeploy the panel, then run the job from the Spark Jobs section.

The panel image build must use the repository root as its Docker context. Use
`make build-panel`, or the equivalent `docker build -f ui/panel/Dockerfile ... .`
form, so the build can read `jobs/*/sparkapplication.yaml`.

## Contract

- The folder name should match the job name used by operators.
- `sparkapplication.yaml` must be a `sparkoperator.k8s.io/v1beta2` `SparkApplication`.
- `metadata.name` must be set and is used for run/rerun/delete/log actions.
- `metadata.namespace` must be `spark`; the panel service account is scoped to that namespace.
- `spec.image` must already be built and loaded into Minikube or available to the cluster.
- The UI panel does not build job images.
- Only `sparkapplication.yaml` files are copied into the panel image; job source, sample data, and build output stay out of the UI image.

For the current Scala log analyzer:

```bash
make prepare-job JOB=log-analyzer-scala
make build-panel
make load-panel
```
