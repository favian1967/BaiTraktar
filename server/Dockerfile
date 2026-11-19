FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 TZ=Europe/Moscow

WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates tzdata \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY fp/ ./fp/
COPY kwork_app/ ./kwork_app/
COPY main.py ./
RUN mkdir -p /app/data

CMD ["python", "main.py"]
