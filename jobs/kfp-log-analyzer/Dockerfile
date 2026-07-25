FROM python:3.11-slim

RUN pip install --no-cache-dir kubernetes==33.1.0

WORKDIR /app
COPY launcher.py /app/launcher.py

USER 65532:65532
ENTRYPOINT ["python", "/app/launcher.py"]
