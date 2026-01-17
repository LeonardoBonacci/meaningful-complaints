# Spring Complaint API

REST API for complaint analysis using Spring Boot, Spring AI, and Ollama with RAG-powered semantic search.

## Features

- 🔍 **Semantic Search**: Find similar complaints using vector embeddings
- 🤖 **RAG-Powered Chat**: Ask questions about complaints with AI using Spring AI's native QuestionAnswerAdvisor
- 📊 **Spring AI PgVectorStore**: Auto-configured vector store with PostgreSQL pgvector extension
- ⚡ **Real-time CDC**: Automatic embedding generation via Flink CDC pipeline
- 🏗️ **Native Spring AI Architecture**: Uses VectorStore, QuestionAnswerAdvisor, and ChatClient patterns

## Verification

See [VERIFICATION_REPORT.md](../VERIFICATION_REPORT.md) for comprehensive testing results demonstrating:
- ✅ 100% success rate in semantic complaint retrieval
- ✅ Real-time CDC integration (<10s latency)
- ✅ Accurate RAG-powered answers grounded in actual customer data

## Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL with pgvector extension (same as Flink CDC pipeline)
- Ollama running locally with llama3.2 model
- Flink CDC pipeline running (for real-time embedding generation)

## Quick Start

1. **Start the application**:
   ```bash
   cd spring-complaint-api
   mvn spring-boot:run
   ```

2. **Access the API**:
   - Base URL: `http://localhost:8080`
   - API Documentation: Check the endpoints below

## API Endpoints

### Complaint Endpoints

#### Get All Complaints
```bash
curl http://localhost:8080/api/complaints
```

Returns all complaints from the database:
```json
[
  {
    "complaintId": 1,
    "customerName": "John Smith",
    "country": "USA",
    "description": "My account was locked after 3 failed login attempts...",
    "createdAt": "2024-01-10T10:30:00"
  },
  ...
]
```

#### Get Complaint by ID
```bash
curl http://localhost:8080/api/complaints/1
```

Returns a single complaint:
```json
{
  "complaintId": 1,
  "customerName": "John Smith",
  "country": "USA",
  "description": "My account was locked after 3 failed login attempts...",
  "createdAt": "2024-01-10T10:30:00"
}
```

#### Create New Complaint
```bash
curl -X POST http://localhost:8080/api/complaints \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Jane Doe",
    "country": "Australia",
    "description": "Product arrived damaged and missing parts. Very disappointed with the delivery service.",
    "createdAt": "2024-01-18T05:30:00"
  }'
```

**🔄 Real-time CDC Integration**: When you create a complaint via this API:
1. The complaint is saved to the PostgreSQL `complaints` table
2. The Flink CDC pipeline automatically detects the change
3. An embedding is generated using Ollama's llama3.2 model
4. The embedding is stored in the `complaint_embeddings` table
5. Your complaint becomes immediately searchable via semantic search

Returns the created complaint:
```json
{
  "complaintId": 9,
  "customerName": "Jane Doe",
  "country": "Australia",
  "description": "Product arrived damaged and missing parts. Very disappointed with the delivery service.",
  "createdAt": "2024-01-18T05:30:00"
}
```

#### Semantic Search
```bash
curl "http://localhost:8080/api/complaints/search?query=account%20locked&limit=5"
```

Finds complaints semantically similar to your query using vector embeddings:
```json
[
  {
    "complaintId": 1,
    "customerName": "John Smith",
    "country": "USA",
    "description": "My account was locked after 3 failed login attempts...",
    "createdAt": "2024-01-10T10:30:00"
  },
  {
    "complaintId": 6,
    "customerName": "Alice Johnson",
    "country": "USA",
    "description": "The app keeps crashing when I try to view my account...",
    "createdAt": "2024-01-14T14:20:00"
  }
]
```

Another example - finding damaged product complaints:
```bash
curl "http://localhost:8080/api/complaints/search?query=damaged%20product&limit=3"
```

### AI Chat Endpoint with RAG

#### Ask About Complaints
This endpoint uses **Spring AI's native Retrieval Augmented Generation (RAG)** with the `QuestionAnswerAdvisor` to answer questions about complaints:
1. Automatically converts your question into a vector embedding
2. Uses Spring's `PgVectorStore` to retrieve semantically similar complaints
3. Passes the retrieved context to Ollama's LLM for a grounded answer

**Architecture:**
- **VectorStore**: Spring's auto-configured `PgVectorStore` with custom database view
- **QuestionAnswerAdvisor**: Native Spring AI advisor for RAG workflows
- **ChatClient**: Fluent API for building prompts with advisors

```bash
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are the most common internet connectivity issues in Mexico?"
  }'
```

Example questions you can ask:
```bash
# Find patterns in complaints by country
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "What problems do customers in Belgium report?"}'

# Get insights about specific issues
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Tell me about WiFi disconnection and billing complaints"}'

# Understand geographic trends
curl -X POST http://localhost:8080/api/chat/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Are there billing issues in both Mexico and Belgium?"}'
```

Returns an AI-generated answer with relevant context:
```json
{
  "answer": "Based on the complaint data from Belgium:\n\n• **WiFi Connectivity Issues** - Pieter Van Den Berg reported WiFi disconnecting every 10 minutes in Brussels office, affecting team productivity\n• **Slow Internet Speeds** - Sophie Dubois in Antwerp is paying for 100Mbps but only getting 15-20Mbps\n• **Billing Errors** - Marc Janssens in Ghent is being charged double (80 euros overcharge) with unresolved customer service issues\n\nThe complaints show a pattern of infrastructure problems and billing accuracy issues across different Belgian cities."
}
```

**How Spring AI RAG Works:**
- `QuestionAnswerAdvisor` orchestrates the RAG workflow
- Embedding model: Ollama llama3.2 (3072 dimensions)
- Vector search: PgVectorStore queries the `vector_store` view with cosine similarity
- LLM receives retrieved complaints as grounded context
- Answer is based on actual customer data from the database

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/complaints_db
    username: postgres
    password: postgres
  
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.2
      embedding:
        options:
          model: llama3.2
    
    vectorstore:
      pgvector:
        # Point to custom database view that joins complaints + embeddings
        table-name: vector_store
        schema-name: public
        dimensions: 3072
        distance-type: COSINE_DISTANCE
        index-type: HNSW
        # Don't create schema - we use existing data
        initialize-schema: false
        remove-existing-vector-store-table: false
        schema-validation: false
```

**Database View Configuration:**
The application uses a custom `vector_store` view that adapts your existing schema to Spring AI's PgVectorStore:

```sql
CREATE VIEW vector_store AS
SELECT 
    c.complaint_id::text as id,
    format('Customer: %s | Country: %s | Description: %s | Created: %s',
           c.customer_name, c.country, c.description, 
           to_char(c.created_at, 'YYYY-MM-DD HH24:MI:SS')) as content,
    jsonb_build_object(
        'complaint_id', c.complaint_id,
        'customer_name', c.customer_name,
        'country', c.country,
        'created_at', c.created_at::text
    ) as metadata,
    ce.embedding
FROM complaints c
JOIN complaint_embeddings ce ON c.complaint_id = ce.complaint_id;
```

## Architecture & Real-time CDC Integration

This API uses **Spring AI's native components** and works seamlessly with the Flink CDC pipeline:

```
┌─────────────────────┐
│  Spring Boot API    │
│  POST /complaints   │
└──────────┬──────────┘
           │ (1) Insert
           ▼
┌─────────────────────┐
│   PostgreSQL DB     │
│  complaints table   │
└──────────┬──────────┘
           │ (2) WAL event
           ▼
┌─────────────────────┐
│   Flink CDC         │
│  Captures change    │
└──────────┬──────────┘
           │ (3) Generate
           ▼
┌─────────────────────┐
│   Ollama API        │
│  llama3.2 model     │
└──────────┬──────────┘
           │ (4) 3072-dim embedding
           ▼
┌─────────────────────┐
│   PostgreSQL DB     │
│complaint_embeddings │
│  + vector_store     │
│      (view)         │
└──────────┬──────────┘
           │ (5) Spring AI RAG
           ▼
┌─────────────────────┐
│  Spring AI Stack    │
│  • PgVectorStore    │
│  • QuestionAnswer   │
│    Advisor          │
│  • ChatClient       │
└─────────────────────┘
```

**Key Components:**
- **Spring Boot 3.2.1**: REST API with Java 17 support
- **Spring AI 1.0.0-M4**: Native RAG support with advisors and vector stores
  - `PgVectorStore`: Auto-configured PostgreSQL vector store
  - `QuestionAnswerAdvisor`: Orchestrates RAG workflow
  - `ChatClient`: Fluent API for LLM interactions
- **Spring Data JPA**: ORM for complaint entities
- **pgvector**: PostgreSQL extension for similarity search
- **Flink CDC**: Real-time change data capture from WAL
- **Ollama**: llama3.2 model for embeddings (3072-dim) and chat

**Spring AI RAG Flow:**
1. User asks question via POST /api/chat/ask
2. `ChatClient` with `QuestionAnswerAdvisor` processes request
3. Question is embedded using Ollama (via Spring AI)
4. `PgVectorStore` queries `vector_store` view with cosine similarity
5. Retrieved complaints are injected as context into LLM prompt
6. Ollama generates grounded answer based on actual data
7. Response returned to user

**Database Schema Integration:**
- Custom `vector_store` view adapts existing schema to Spring AI format
- View joins `complaints` and `complaint_embeddings` tables
- No schema changes needed - Spring AI works with existing data

## Development

Build the project:
```bash
mvn clean package
```

Run tests:
```bash
mvn test
```

Run with custom profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
