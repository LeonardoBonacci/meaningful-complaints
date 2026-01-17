# Meaningful Complaints

Multi-application repository for complaint analysis and semantic search with real-time CDC and Spring AI-powered RAG.

## Applications

### [flink-cdc-pipeline](./flink-cdc-pipeline/)
Real-time CDC pipeline using Apache Flink to capture PostgreSQL changes, generate embeddings with Ollama, and enable semantic search with pgvector.

### [spring-complaint-api](./spring-complaint-api/)
REST API built with Spring Boot and Spring AI for complaint analysis. Features include:
- Native Spring AI RAG with `QuestionAnswerAdvisor`
- Auto-configured `PgVectorStore` for semantic search
- Real-time integration with Flink CDC pipeline
- AI-powered chat endpoint using Ollama

## Architecture

The system combines real-time CDC with Spring AI's native RAG capabilities:
1. **Flink CDC** captures database changes and generates embeddings
2. **Spring AI PgVectorStore** provides vector similarity search
3. **QuestionAnswerAdvisor** orchestrates retrieval-augmented generation
4. **Ollama** provides embeddings and chat completions

## Getting Started

See individual application directories for detailed setup instructions.
