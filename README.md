# Semantic Pipeline for Customer Complaints

## Source
- Postgres table (e.g., `complaints`)
- Expose via Flink CDC connector (Debezium internally, but managed by Flink)

## Processing: Flink Job
- Listens to insert/update/delete events
- For each event:
  1. Extract textual fields (`description + resolution_notes`)
  2. Call embedding model (LLM, OpenAI, etc.)
  3. Store/update embedding in sidecar table (`complaint_embeddings`)

## Sidecar Table: Postgres + pgvector (Semantic Layer)
- Stores `complaint_id` + `embedding`
- Optional timestamp/versioning

## Consumers
- **BA**: queries semantic + structured data
- **LLM / RAG**: summarizes or explains trends
- **Agentic AI**: can reason over new complaints immediately

```
docker run -d \
  --name pg-semantic-poc \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=complaints_db \
  -p 5432:5432 \
  pgvector/pgvector:pg16

docker run -it --rm \
  --network host \
  postgres:16 \
  psql -h localhost -U postgres -d complaints_db

CREATE EXTENSION IF NOT EXISTS vector;

\dx

CREATE TABLE complaints (
    complaint_id BIGINT PRIMARY KEY,
    customer_name TEXT,
    country TEXT,
    description TEXT,
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE complaint_embeddings (
    complaint_id BIGINT PRIMARY KEY,
    embedding VECTOR(3072),  -- llama3.2 embedding dimension
    updated_at TIMESTAMP DEFAULT now()
);

INSERT INTO complaint_embeddings (complaint_id, embedding)
VALUES
  ('00000000-0000-0000-0000-000000000001', NULL),
  ('00000000-0000-0000-0000-000000000002', NULL);

```
