package guru.bonacci.semantic;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import org.apache.flink.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import org.apache.flink.cdc.connectors.postgres.source.PostgresSourceBuilder;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.embeddings.OllamaEmbeddingsRequestModel;
import io.github.ollama4j.models.embeddings.OllamaEmbeddingResponseModel;

import java.util.List;

public class SemanticCDCJob {

    public static void main(String[] args) throws Exception {

        // 1️⃣ Define the Postgres CDC source
        JdbcIncrementalSource<String> sourceFunction =
                PostgresSourceBuilder.PostgresIncrementalSource.<String>builder()
                        .hostname("localhost")
                        .port(5432)
                        .database("complaints_db")        // monitor your complaints database
                        .schemaList("public")             // monitor public schema
                        .tableList("public.complaints")   // monitor complaints table
                        .username("postgres")
                        .password("postgres")
                        .slotName("flink")                // replication slot name
                        .decodingPluginName("pgoutput")   // use built-in plugin (PG 10+)
                        .deserializer(new JsonDebeziumDeserializationSchema()) // emits JSON string
                        .build();

        // 2️⃣ Create Flink environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 3️⃣ Process CDC events: parse JSON, extract description, generate embedding
        env.fromSource(sourceFunction, WatermarkStrategy.noWatermarks(), "Postgres CDC Source")
                .setParallelism(1)
                .map(new ComplaintEmbeddingMapper())
                .print();

        // 4️⃣ Execute the job
        env.execute("Complaints CDC with Embeddings");
    }

    /**
     * MapFunction to parse CDC JSON and generate embeddings
     */
    public static class ComplaintEmbeddingMapper implements MapFunction<String, String> {
        
        private static final ObjectMapper objectMapper = new ObjectMapper();
        private static final OllamaAPI ollama = new OllamaAPI("http://localhost:11434");
        
        @Override
        public String map(String jsonEvent) throws Exception {
            // Parse the Debezium CDC JSON
            JsonNode root = objectMapper.readTree(jsonEvent);
            JsonNode after = root.get("after");
            
            if (after != null && !after.isNull()) {
                long complaintId = after.get("complaint_id").asLong();
                String description = after.get("description").asText();
                
                // Generate embedding using Ollama
                OllamaEmbeddingsRequestModel request = new OllamaEmbeddingsRequestModel("llama3.2:latest", description);
                List<Double> embedding = ollama.generateEmbeddings(request);
                
                // Return formatted result
                return String.format("Complaint ID: %d, Description: %s, Embedding dim: %d", 
                    complaintId, description, embedding.size());
            }
            
            return "No data in 'after' field";
        }
    }
}
