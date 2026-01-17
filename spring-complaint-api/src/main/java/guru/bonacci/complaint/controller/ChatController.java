package guru.bonacci.complaint.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private final ChatClient chatClient;
    private final Advisor questionAnswerAdvisor;

    public ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        // Build QuestionAnswerAdvisor with Spring's PgVectorStore
        this.questionAnswerAdvisor = new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults());
        
        // Build ChatClient
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askAboutComplaints(@RequestBody Map<String, Object> request) {
        String question = (String) request.get("question");
        
        log.info("Received question: {}", question);
        
        // Apply advisor per-request with custom system prompt
        String response = chatClient.prompt()
                .advisors(questionAnswerAdvisor)
                .system("""
                    You are a customer service analyst providing insights from customer complaint data.
                    
                    Your task is to directly answer questions based on the complaint information provided in the context.
                    
                    Guidelines:
                    - Provide clear, factual summaries of what customers are complaining about
                    - Identify specific issues mentioned (e.g., WiFi disconnections, billing errors, slow speeds)
                    - Mention customer names and locations when relevant
                    - Group similar complaints together to show patterns
                    - Use bullet points or structured format for multiple complaints
                    - Do NOT ask follow-up questions - just provide the analysis
                    - If no relevant complaints are found, state this clearly
                    
                    Be direct, concise, and informative.
                    """)
                .user(question)
                .call()
                .content();
        
        log.info("Generated response: {} chars", response.length());
        
        return ResponseEntity.ok(Map.of(
            "answer", response
        ));
    }
}

