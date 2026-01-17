import psycopg2
import ollama

# Postgres connection
conn = psycopg2.connect(
    host="localhost",
    port=5432,
    dbname="complaints_db",
    user="postgres",
    password="postgres"
)
cur = conn.cursor()

# LLM model
model_name = "llama3.2:latest"

# Fetch complaints
cur.execute("SELECT complaint_id, description FROM complaints")
complaints = cur.fetchall()  # list of tuples: (complaint_id, description)

# Insert or update embeddings
for complaint_id, description in complaints:
    # Generate embedding
    response = ollama.embed(model=model_name, input=description)
    embedding_vector = response.embeddings[0]  # single vector

    # Upsert into complaint_embeddings table
    cur.execute("""
        INSERT INTO complaint_embeddings (complaint_id, embedding)
        VALUES (%s, %s)
        ON CONFLICT (complaint_id) DO UPDATE
        SET embedding = EXCLUDED.embedding, updated_at = now()
    """, (complaint_id, embedding_vector))

conn.commit()
cur.close()
conn.close()

print("Embeddings generated and stored successfully.")
