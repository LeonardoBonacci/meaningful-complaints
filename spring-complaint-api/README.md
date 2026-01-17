# Spring Complaint API

REST API for complaint analysis using Spring Boot, Spring AI, and Ollama.

## Features

- 🔍 **Semantic Search**: Find similar complaints using vector embeddings
- 🤖 **AI-Powered Analysis**: Analyze complaint sentiment, category, and urgency
- 💬 **Response Suggestions**: Generate professional responses to complaints
- 📊 **PostgreSQL Integration**: Access complaints with pgvector for similarity search

## Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL with pgvector extension (same as Flink CDC pipeline)
- Ollama running locally with llama3.2 model

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

### AI Chat Endpoints

#### Analyze Complaint
```bash
curl -X POST http://localhost:8080/api/chat/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "complaint": "My account was locked after 3 failed login attempts. I tried calling support but waited on hold for 2 hours!"
  }'
```

Returns AI-powered analysis:
```json
{
  "sentiment": "negative",
  "category": "account access",
  "urgency": "high",
  "keyIssues": ["account locked", "support wait time"],
  "suggestedActions": ["unlock account immediately", "improve support response time"]
}
```

#### Suggest Response
```bash
curl -X POST http://localhost:8080/api/chat/suggest-response \
  -H "Content-Type: application/json" \
  -d '{
    "complaint": "Product arrived damaged and missing parts. Very disappointed with the delivery service.",
    "tone": "professional"
  }'
```

Returns a professional response suggestion:
```text
Dear Jane Doe,

We sincerely apologize for the damaged product and missing parts you received. This does not meet our quality standards, and we understand your disappointment.

We will immediately process a replacement shipment with expedited delivery at no charge. Additionally, we're investigating this with our delivery partner to prevent future occurrences.

Please accept a 20% discount on your next order as a gesture of our commitment to your satisfaction.

Best regards,
Customer Service Team
```

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
```

## Architecture & Real-time CDC Integration

This API works seamlessly with the Flink CDC pipeline for real-time synchronization:

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
└──────────┬──────────┘
           │ (5) Vector search
           ▼
┌─────────────────────┐
│  Spring Boot API    │
│  GET /search        │
└─────────────────────┘
```

**Key Components:**
- **Spring Boot 3.2.1**: REST API with Java 17 support
- **Spring AI**: Ollama integration for embeddings and chat
- **Spring Data JPA**: ORM with custom vector queries
- **pgvector**: PostgreSQL extension for similarity search
- **Flink CDC**: Real-time change data capture from WAL
- **Ollama**: llama3.2 model for 3072-dimensional embeddings

**Data Flow:**
1. Create complaint via POST → saved to `complaints` table
2. Flink CDC detects insert via PostgreSQL WAL (Write-Ahead Log)
3. Ollama generates 3072-dimensional embedding
4. JDBC sink writes to `complaint_embeddings` table with UPSERT
5. Semantic search immediately available via GET /search

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
