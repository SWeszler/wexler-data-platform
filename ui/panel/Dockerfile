FROM python:3.11-slim

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV SPARK_JOBS_DIR=/app/jobs

COPY ui/panel/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY ui/panel/*.py .

EXPOSE 8501

CMD ["streamlit", "run", "app.py", "--server.address=0.0.0.0", "--server.port=8501", "--browser.gatherUsageStats=false"]
