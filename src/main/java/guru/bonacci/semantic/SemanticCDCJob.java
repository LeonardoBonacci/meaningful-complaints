package guru.bonacci.semantic;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;

import org.apache.flink.cdc.connectors.base.source.jdbc.JdbcIncrementalSource;
import org.apache.flink.cdc.connectors.postgres.source.PostgresSourceBuilder;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.embeddings.OllamaEmbeddingsRequestModel;

import java.sql.PreparedStatement;
import java.sql.SQLException;
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

        // 3️⃣ Process CDC events: parse JSON, extract description, generate embedding, write to DB
        env.fromSource(sourceFunction, WatermarkStrategy.noWatermarks(), "Postgres CDC Source")
                .setParallelism(1)
                .map(new ComplaintEmbeddingMapper())
                .filter(embedding -> embedding != null)  // Filter out null results
                .addSink(
                    JdbcSink.sink(
                        "INSERT INTO complaint_embeddings (complaint_id, embedding, updated_at) " +
                        "VALUES (?, ?::vector, now()) " +
                        "ON CONFLICT (complaint_id) DO UPDATE SET embedding = EXCLUDED.embedding, updated_at = now()",
                        new EmbeddingStatementBuilder(),
                        JdbcExecutionOptions.builder()
                                .withBatchSize(1)
                                .withBatchIntervalMs(200)
                                .withMaxRetries(3)
                                .build(),
                        new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                                .withUrl("jdbc:postgresql://localhost:5432/complaints_db")
                                .withDriverName("org.postgresql.Driver")
                                .withUsername("postgres")
                                .withPassword("postgres")
                                .build()
                    )
                );

        // 4️⃣ Execute the job
        env.execute("Complaints CDC with Embeddings");
    }

    /**
     * MapFunction to parse CDC JSON and generate embeddings
     */
    public static class ComplaintEmbeddingMapper implements MapFunction<String, ComplaintEmbedding> {
        
        private static final ObjectMapper objectMapper = new ObjectMapper();
        private static final OllamaAPI ollama = new OllamaAPI("http://localhost:11434");
        
        @Override
        public ComplaintEmbedding map(String jsonEvent) throws Exception {
            // Parse the Debezium CDC JSON
            JsonNode root = objectMapper.readTree(jsonEvent);
            JsonNode after = root.get("after");
            
            if (after != null && !after.isNull()) {
                long complaintId = after.get("complaint_id").asLong();
                String description = after.get("description").asText();
                
                // Generate embedding using Ollama
                OllamaEmbeddingsRequestModel request = new OllamaEmbeddingsRequestModel("llama3.2:latest", description);
                List<Double> embeddingList = ollama.generateEmbeddings(request);
                
                // Convert List<Double> to double[] for better serialization
                double[] embedding = new double[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embedding[i] = embeddingList.get(i);
                }
                
                System.out.println(String.format("Generated embedding for complaint %d (dim: %d)", 
                    complaintId, embedding.length));
                
                return new ComplaintEmbedding(complaintId, embedding);
            }
            
            return null;
        }
    }

    /**
     * POJO for complaint embedding
     */
    public static class ComplaintEmbedding implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        public long complaintId;
        public double[] embedding;
        
        public ComplaintEmbedding() {}
        
        public ComplaintEmbedding(long complaintId, double[] embedding) {
            this.complaintId = complaintId;
            this.embedding = embedding;
        }
    }

    /**
     * Statement builder for JDBC sink
     */
    public static class EmbeddingStatementBuilder implements JdbcStatementBuilder<ComplaintEmbedding> {
        @Override
        public void accept(PreparedStatement ps, ComplaintEmbedding ce) throws SQLException {
            ps.setLong(1, ce.complaintId);
            
            // Convert double[] to PostgreSQL vector format: [1.0,2.0,3.0]
            StringBuilder vectorStr = new StringBuilder("[");
            for (int i = 0; i < ce.embedding.length; i++) {
                if (i > 0) vectorStr.append(",");
                vectorStr.append(ce.embedding[i]);
            }
            vectorStr.append("]");
            
            ps.setString(2, vectorStr.toString());
        }
    }
}
