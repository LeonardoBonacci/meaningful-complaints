import ollama

# Texts you want to embed (test)
text = "Refund request pending for over a week"

model_name = "llama3.2:latest"

# Generate embedding
response = ollama.embed(model=model_name, input=text)

# The embeddings attribute is a list of vectors
embedding_vector = response.embeddings[0]  # get the first (and only) vector

print(f"Embedding dimension: {len(embedding_vector)}")

#  Embedding dimension: 3072