FROM debian:12.7-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates samtools && \
    rm -rf /var/lib/apt/lists/*

USER 1000
