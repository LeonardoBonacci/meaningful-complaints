import psycopg2
import ollama

# ---------------------------
# Configuration
# ---------------------------
DB_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "dbname": "complaints_db",
    "user": "postgres",
    "password": "postgres"
}

MODEL_NAME = "llama3.2:latest"
TOP_K = 1

# ---------------------------
# New complaint to test
# ---------------------------
new_complaint = "I still have not received my refund after two weeks"

# ---------------------------
# Generate embedding for new complaint
# ---------------------------
embedding_response = ollama.embed(model=MODEL_NAME, input=new_complaint)
query_vector = embedding_response.embeddings[0]  # list of floats

# ---------------------------
# Connect to Postgres
# ---------------------------
conn = psycopg2.connect(**DB_CONFIG)
cur = conn.cursor()

# ---------------------------
# Perform semantic search
# ---------------------------
# We pass the embedding as a PostgreSQL vector literal
vector_literal = str(query_vector)  # '[0.12, -0.33, ...]'

sql = f"""
SELECT c.complaint_id, c.description
FROM complaint_embeddings e
JOIN complaints c USING (complaint_id)
ORDER BY e.embedding <-> '{vector_literal}'::vector
LIMIT {TOP_K};
"""

cur.execute(sql)
results = cur.fetchall()

print("\nTop similar complaints:\n")
for complaint_id, description in results:
    print(f"ID: {complaint_id}\nDescription: {description}\n")

cur.close()
conn.close()
