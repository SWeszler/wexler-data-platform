#!/bin/bash
set -e

export SPARK_APPLICATION_DEPLOY_MODE=${SPARK_APPLICATION_DEPLOY_MODE:-client}

case "$SPARK_APPLICATION_DEPLOY_MODE" in
	client)
		/submit-client.sh
		;;
	cluster)
		/submit-cluster.sh
		;;
	*)
		echo "Unsupported SPARK_APPLICATION_DEPLOY_MODE=${SPARK_APPLICATION_DEPLOY_MODE}. Expected client or cluster."
		exit 1
		;;
esac
