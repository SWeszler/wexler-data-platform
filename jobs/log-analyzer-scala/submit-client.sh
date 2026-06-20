#!/bin/bash
set -e

export SPARK_HOME=${SPARK_HOME:-/opt/spark-3.0.0}
export SPARK_MASTER_NAME=${SPARK_MASTER_NAME:-spark-master}
export SPARK_MASTER_PORT=${SPARK_MASTER_PORT:-7077}
export SPARK_MASTER_URL=${SPARK_MASTER_URL:-spark://${SPARK_MASTER_NAME}:${SPARK_MASTER_PORT}}

if [ -z "$SPARK_APPLICATION_JAR_LOCATION" ]; then
	SPARK_APPLICATION_JAR_LOCATION=`find /app/target -iname '*-assembly-*.jar' | head -n1`
	export SPARK_APPLICATION_JAR_LOCATION
fi

if [ -z "$SPARK_APPLICATION_JAR_LOCATION" ]; then
	echo "Can't find a file *-assembly-*.jar in /app/target"
	exit 1
fi

echo "Submit application ${SPARK_APPLICATION_JAR_LOCATION} with main class ${SPARK_APPLICATION_MAIN_CLASS} to Spark master ${SPARK_MASTER_URL}"
echo "Deploy mode client"
echo "Passing Spark submit args ${SPARK_SUBMIT_ARGS}"
echo "Passing application args ${SPARK_APPLICATION_ARGS}"

${SPARK_HOME}/bin/spark-submit \
	--class ${SPARK_APPLICATION_MAIN_CLASS} \
	--master ${SPARK_MASTER_URL} \
	--deploy-mode client \
	${SPARK_SUBMIT_ARGS} \
	${SPARK_APPLICATION_JAR_LOCATION} ${SPARK_APPLICATION_ARGS}
