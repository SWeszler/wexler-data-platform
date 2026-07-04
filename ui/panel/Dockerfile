FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV SPARK_JOBS_DIR=/app/jobs

COPY ui/panel/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ui/panel/*.py .
COPY jobs ./source-jobs
RUN set -eu; \
	for manifest in ./source-jobs/*/sparkapplication.yaml; do \
		job_dir="$(basename "$(dirname "$manifest")")"; \
		mkdir -p "./jobs/${job_dir}"; \
		cp "$manifest" "./jobs/${job_dir}/sparkapplication.yaml"; \
	done; \
	rm -rf ./source-jobs

EXPOSE 8501

CMD ["streamlit", "run", "app.py", "--server.address=0.0.0.0", "--server.port=8501", "--browser.gatherUsageStats=false"]
