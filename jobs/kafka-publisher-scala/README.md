# Kafka Publisher Scala Spark Job

This job publishes a small batch of synthetic JSON events to Kafka. It is intended
as a smoke test for the Minikube Kafka cluster and Kafbat UI.

## Defaults

- Kafka bootstrap servers: `wexler-kafka-kafka-bootstrap.data.svc.cluster.local:9092`
- Topic: `spark-smoke-events`
- Event count: `25`

## Build and load

```bash
make prepare-job JOB=kafka-publisher-scala
```

Rebuild and load the panel if you want the job to appear in the UI:

```bash
make build-panel
make load-panel
kubectl apply -f ./k8s/platform/ui-panel.yaml
```

## Run

Create the Kafka topic first:

```bash
kubectl apply -f ./k8s/platform/kafka-topics.yaml
```

Submit the Spark job:

```bash
kubectl apply -f ./jobs/kafka-publisher-scala/sparkapplication.yaml
kubectl get sparkapplication -n spark --watch
```

Inspect the driver logs:

```bash
kubectl logs -n spark kafka-publisher-scala-driver
```

## Verify

Open `http://kafka-ui.wexler.test`, select the `wexler-kafka` cluster, and
inspect topic `spark-smoke-events`.

You can also consume a few messages from the Kafka pod:

```bash
kubectl -n data exec -it wexler-kafka-kafka-0 -- \
  bin/kafka-console-consumer.sh \
  --bootstrap-server wexler-kafka-kafka-bootstrap:9092 \
  --topic spark-smoke-events \
  --from-beginning \
  --max-messages 5
```
