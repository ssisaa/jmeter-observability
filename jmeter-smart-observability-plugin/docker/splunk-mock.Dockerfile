# Splunk HEC mock - single-file FastAPI that echoes every POST /services/collector
FROM python:3.12-slim

RUN pip install --no-cache-dir "fastapi[standard]==0.115.4" "uvicorn==0.32.0"

COPY docker/splunk_mock.py /app/splunk_mock.py
WORKDIR /app

EXPOSE 8088

CMD ["uvicorn", "splunk_mock:app", "--host", "0.0.0.0", "--port", "8088"]
