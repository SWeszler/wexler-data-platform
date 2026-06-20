# Docker multi-container environment with Hadoop, Spark and Hive

This is it: a Docker multi-container environment with Hadoop (HDFS), Spark and Hive. But without the large memory requirements of a Cloudera sandbox. (On my Windows 10 laptop (with WSL2) it seems to consume a mere 3 GB.)

The only thing lacking, is that Hive server doesn't start automatically. To be added when I understand how to do that in docker-compose.

## Architecture Overview

Here is a visual breakdown of all the containers running in this environment and exactly what their roles are:

```mermaid
graph TD
    subgraph HDFS ["Hadoop Distributed File System (Storage)"]
        NN[namenode<br/>Master: Manages file metadata & directory tree]
        DN[datanode<br/>Worker: Stores the actual data blocks]
        NN --- DN
    end

    subgraph YARN ["Hadoop YARN (Resource Management)"]
        RM[resourcemanager<br/>Master: Allocates cluster resources]
        NM[nodemanager<br/>Worker: Runs containers & jobs]
        HS[historyserver<br/>UI: Keeps history of finished jobs]
        RM --- NM
        RM -.-> HS
    end

    subgraph SPARK ["Apache Spark (Compute Engine)"]
        SM[spark-master<br/>Master: Manages Spark applications]
        SW[spark-worker-1<br/>Worker: Executes Spark tasks in memory]
        SM --- SW
    end

    subgraph HIVE ["Apache Hive (Data Warehouse)"]
        HS2[hive-server<br/>SQL Interface for batch queries]
        HM[hive-metastore<br/>Stores table schemas & locations]
        PG[(hive-metastore-postgresql<br/>Relational DB holding metadata)]
        HS2 --> HM
        HM --> PG
    end

    subgraph PRESTO ["Presto (Fast SQL Engine)"]
        PC[presto-coordinator<br/>Interactive SQL queries in-memory]
    end

    %% Key Data Flows
    SM -. "Reads/Writes" .-> NN
    NM -. "Accesses Data" .-> NN
    HS2 -. "Reads/Writes" .-> NN
    PC -. "Gets Schemas" .-> HM
    PC -. "Reads Data directly" .-> NN
```


## Quick Start

To deploy the HDFS-Spark-Hive cluster, run:
```
  docker-compose up
```

Hive metastore data is stored in the `hive_metastore_postgresql` named volume, and HDFS data is stored in the Hadoop named volumes. Use `docker-compose down` to stop containers while keeping Hive table metadata and warehouse files. Avoid `docker-compose down -v` unless you intentionally want to delete those volumes.

`docker-compose` creates a docker network that can be found by running `docker network list`, e.g. `wexler-data-platform_default`.

Web UIs (how to access)

The Hadoop/Spark services expose several helpful web interfaces. If running Docker on your laptop (Docker Desktop), most interfaces are available via localhost. If using a remote Docker host, substitute <dockerhadoop_IP_address> with the host IP found via `docker network inspect`.

Common URLs (localhost or <dockerhadoop_IP_address>):

Below is the current accessibility status for each UI (why a URL like http://localhost:8188/applicationhistory may not work): many services run inside containers and are not published to the host by default. If a port is not mapped in docker-compose.yml you cannot reach it via localhost.

Published to host (accessible via localhost):

* Namenode (HDFS web UI / Explorer): http://localhost:9870/explorer.html#/  — mapped in docker-compose (9870:9870)
* DataNode: http://localhost:9864/  — mapped (9864:9864)
* Spark Master (cluster UI): http://localhost:8080/  — mapped (8080:8080)
* Spark worker: http://localhost:8081/  — mapped (8081:8081)
* Spark Application UI (per-app, default port on driver): http://localhost:4040/ for client-mode drivers on spark-master, or http://localhost:4041/ for cluster-mode drivers on spark-worker-1. Only visible while an application runs.
* HiveServer2 (JDBC): port 10000 is published (JDBC, not a web UI): connect with `jdbc:hive2://localhost:10000`
* Hive metastore service: http://localhost:9083/ (thrift) — mapped (9083:9083) but not a browser dashboard
* Trino coordinator UI: http://localhost:8089/  — mapped (host 8089 -> container 8080)

Not published to host by default (internal-only — reachable from other containers):

* ResourceManager (YARN): internal port 8088 — not published in docker-compose. Example why http://localhost:8088/ fails. Options: publish 8088:8088 in docker-compose, curl the service from a container (`docker exec -it namenode curl http://resourcemanager:8088/`), or use the container IP.
* NodeManager (node details): internal port 8042 — not published by default. See same access options as ResourceManager.
* MapReduce / History Server (job history): internal port 8188 — not published, so http://localhost:8188/applicationhistory will fail unless port is published.
* Spark History Server: not configured/published in this compose file (so http://localhost:18080 will not be available).

Other reasons a UI may be unreachable:

* Service not yet started or failed to start — check container logs (`docker logs <container>`).
* The UI is per-application and only exists while a job runs (e.g., Spark driver UI at 4040 shows only during app lifetime).
* Docker Desktop networking (on macOS/Windows) sometimes routes differently — use published host ports or exec into a container to curl internals.

If you want, the compose file can be updated to publish additional ports (e.g., 8088, 8042, 8188) so all UIs work via localhost. Otherwise use the container network or inspect container IPs to access internal dashboards.

These notes should clarify why some URLs (like http://localhost:8188/applicationhistory) are not accessible by default and what to do about it.

## Quick Start HDFS

Open a shell on the namenode:
```bash
  docker exec -it namenode bash
```

Create a directory and upload a file:
```bash
  hdfs dfs -mkdir -p /data/example
  echo "hello world" > sample.txt
  hdfs dfs -put sample.txt /data/example/
  hdfs dfs -cat /data/example/sample.txt
```


## Interactive Querying with Spark Shells

Spark provides interactive REPL shells for ad-hoc data exploration. You can use either the Scala shell (`spark-shell`) or the Python shell (`pyspark`).

Exec into the `spark-master` container and launch a shell:
```bash
  docker exec -it spark-master bash

  # Scala
  /spark/bin/spark-shell --master spark://spark-master:7077

  # or Python
  /spark/bin/pyspark --master spark://spark-master:7077
```

Run a quick test to verify the cluster is working:
```scala
  val df = spark.range(1000).toDF("id")
  df.show(5)
```


## Quick Start Hive

Connect to the Hive server using Beeline:
```bash
  docker exec -it hive-server bash

  beeline -u jdbc:hive2://localhost:10000 -n root
```

Verify Hive is working:
```sql
  show databases;

  create database IF NOT EXISTS test_db;
  use test_db;

  CREATE TABLE IF NOT EXISTS sample (id INT, name STRING);
  INSERT INTO sample VALUES (1, 'hello'), (2, 'world');
  SELECT * FROM sample;
```

There you go: your private Hive server to play with.


## Configure Environment Variables

The configuration parameters can be specified in the hadoop.env file or as environmental variables for specific services (e.g. namenode, datanode etc.):
```
  CORE_CONF_fs_defaultFS=hdfs://namenode:8020
```

CORE_CONF corresponds to core-site.xml. fs_defaultFS=hdfs://namenode:8020 will be transformed into:
```
  <property><name>fs.defaultFS</name><value>hdfs://namenode:8020</value></property>
```
To define dash inside a configuration parameter, use triple underscore, such as YARN_CONF_yarn_log___aggregation___enable=true (yarn-site.xml):
```
  <property><name>yarn.log-aggregation-enable</name><value>true</value></property>
```

The available configurations are:
* /etc/hadoop/core-site.xml CORE_CONF
* /etc/hadoop/hdfs-site.xml HDFS_CONF
* /etc/hadoop/yarn-site.xml YARN_CONF
* /etc/hadoop/httpfs-site.xml HTTPFS_CONF
* /etc/hadoop/kms-site.xml KMS_CONF
* /etc/hadoop/mapred-site.xml  MAPRED_CONF

If you need to extend some other configuration file, refer to base/entrypoint.sh bash script.
