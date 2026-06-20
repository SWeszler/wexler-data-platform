#!/bin/bash
set -e

export SPARK_HOME=${SPARK_HOME:-/opt/spark-3.0.0}
export SPARK_MASTER_NAME=${SPARK_MASTER_NAME:-spark-master}
export SPARK_MASTER_PORT=${SPARK_MASTER_PORT:-7077}
export SPARK_MASTER_URL=${SPARK_MASTER_URL:-spark://${SPARK_MASTER_NAME}:${SPARK_MASTER_PORT}}
export HDFS_DEFAULT_FS=${HDFS_DEFAULT_FS:-hdfs://namenode:9000}
export DEFAULT_SPARK_APPLICATION_JAR_LOCATION="${HDFS_DEFAULT_FS}/apps/log-analyzer-scala/log-analyzer-scala.jar"

LOCAL_SPARK_APPLICATION_JAR_LOCATION=`find /app/target -iname '*-assembly-*.jar' | head -n1`

if [ -z "$SPARK_APPLICATION_JAR_LOCATION" ]; then
	SPARK_APPLICATION_JAR_LOCATION="$DEFAULT_SPARK_APPLICATION_JAR_LOCATION"
	export SPARK_APPLICATION_JAR_LOCATION
	echo "Using default cluster application JAR location ${SPARK_APPLICATION_JAR_LOCATION}"
fi

publish_jar_to_hdfs() {
	if [ -z "$LOCAL_SPARK_APPLICATION_JAR_LOCATION" ]; then
		echo "Can't find a file *-assembly-*.jar in /app/target"
		exit 1
	fi

	case "$SPARK_APPLICATION_JAR_LOCATION" in
		hdfs://*)
			;;
		*)
			echo "Cluster mode publishes the bundled JAR to HDFS, so SPARK_APPLICATION_JAR_LOCATION must be an hdfs:// path."
			echo "Unset SPARK_APPLICATION_JAR_LOCATION to use the default ${DEFAULT_SPARK_APPLICATION_JAR_LOCATION}."
			exit 1
			;;
	esac

	HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-/tmp/hadoop-conf}
	export HADOOP_CONF_DIR
	mkdir -p "$HADOOP_CONF_DIR"
	cat > "$HADOOP_CONF_DIR/core-site.xml" <<EOF
<?xml version="1.0"?>
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>${HDFS_DEFAULT_FS}</value>
  </property>
</configuration>
EOF

	HDFS_JAR_DIR=${SPARK_APPLICATION_JAR_LOCATION%/*}
	echo "Publishing application JAR ${LOCAL_SPARK_APPLICATION_JAR_LOCATION} to ${SPARK_APPLICATION_JAR_LOCATION}"
	${SPARK_HOME}/bin/spark-class org.apache.hadoop.fs.FsShell -mkdir -p "$HDFS_JAR_DIR"
	${SPARK_HOME}/bin/spark-class org.apache.hadoop.fs.FsShell -put -f "$LOCAL_SPARK_APPLICATION_JAR_LOCATION" "$SPARK_APPLICATION_JAR_LOCATION"
}

publish_jar_to_hdfs

echo "Submit application ${SPARK_APPLICATION_JAR_LOCATION} with main class ${SPARK_APPLICATION_MAIN_CLASS} to Spark master ${SPARK_MASTER_URL}"
echo "Deploy mode cluster"
echo "Passing Spark submit args ${SPARK_SUBMIT_ARGS}"
echo "Passing application args ${SPARK_APPLICATION_ARGS}"

# Remove after Spark master/worker and submit images use the same Java path.
unset JAVA_HOME

${SPARK_HOME}/bin/spark-submit \
	--class ${SPARK_APPLICATION_MAIN_CLASS} \
	--master ${SPARK_MASTER_URL} \
	--deploy-mode cluster \
	${SPARK_SUBMIT_ARGS} \
	${SPARK_APPLICATION_JAR_LOCATION} ${SPARK_APPLICATION_ARGS}
