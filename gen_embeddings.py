import psycopg2
import ollama

# Connect to Postgres
conn = psycopg2.connect(
    host="localhost",
    port=5432,
    dbname="complaints_db",
    user="postgres",
    password="postgres"
)
cur = conn.cursor()

# Example complaints
complaints = [
    ("00000000-0000-0000-0000-000000000001", "Refund request pending for over a week"),
    ("00000000-0000-0000-0000-000000000002", "Complaint about wrong product delivered")
]

model_name = "llama3.2:latest"

for complaint_id, text in complaints:
    # Generate embedding
    response = ollama.embed(model=model_name, input=text)
    embedding_vector = response.embeddings[0]
    
    # Update the sidecar table
    cur.execute(
        "UPDATE complaint_embeddings SET embedding = %s WHERE complaint_id = %s",
        (embedding_vector, complaint_id)
    )

conn.commit()
cur.close()
conn.close()
