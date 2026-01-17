#!/usr/bin/env python3
"""
Semantic search example: Business Analyst searching for complaints
"""
import requests
import psycopg2
import json

# Search query from imaginary BA
SEARCH_QUERY = "I cannot access my account and it seems to be locked"

print(f"🔍 Searching for complaints similar to: '{SEARCH_QUERY}'\n")

# Step 1: Generate embedding for the search query using Ollama
print("Generating embedding for search query...")
ollama_url = "http://localhost:11434/api/embeddings"
response = requests.post(ollama_url, json={
    "model": "llama3.2",
    "prompt": SEARCH_QUERY
})
search_embedding = response.json()["embedding"]
print(f"✓ Generated embedding (dimension: {len(search_embedding)})\n")

# Step 2: Query PostgreSQL for similar complaints
print("Searching database for similar complaints...\n")
conn = psycopg2.connect(
    host="localhost",
    port=5432,
    database="complaints_db",
    user="postgres",
    password="postgres"
)

cursor = conn.cursor()

# Use cosine distance operator <=> for similarity search
query = """
SELECT 
    c.complaint_id,
    c.customer_name,
    c.country,
    c.description,
    ce.embedding <=> %s::vector as similarity_distance
FROM complaints c
JOIN complaint_embeddings ce ON c.complaint_id = ce.complaint_id
ORDER BY ce.embedding <=> %s::vector
LIMIT 5;
"""

# Convert Python list to PostgreSQL vector format
embedding_str = str(search_embedding)

cursor.execute(query, (embedding_str, embedding_str))
results = cursor.fetchall()

print("=" * 100)
print(f"{'Rank':<6} {'ID':<5} {'Customer':<20} {'Country':<10} {'Distance':<12} {'Description':<50}")
print("=" * 100)

for idx, (complaint_id, customer_name, country, description, distance) in enumerate(results, 1):
    desc_preview = description[:47] + "..." if len(description) > 50 else description
    print(f"{idx:<6} {complaint_id:<5} {customer_name:<20} {country:<10} {distance:<12.6f} {desc_preview}")

print("=" * 100)
print(f"\n✓ Found {len(results)} most similar complaints")
print("\nNote: Lower distance = more similar to search query")

cursor.close()
conn.close()
