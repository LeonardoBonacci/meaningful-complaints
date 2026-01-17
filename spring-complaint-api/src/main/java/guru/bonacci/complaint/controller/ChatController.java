package guru.bonacci.complaint.controller;

import guru.bonacci.complaint.model.Complaint;
import guru.bonacci.complaint.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;
    private final ComplaintService complaintService;

    private ChatClient getChatClient() {
        return chatClientBuilder.build();
    }

    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askAboutComplaints(@RequestBody Map<String, Object> request) {
        String question = (String) request.get("question");
        Integer limit = request.containsKey("limit") ? (Integer) request.get("limit") : 5;
        
        log.info("Received question: {}", question);
        
        // Step 1: Retrieve relevant complaints using vector similarity (RAG retrieval)
        List<Complaint> relevantComplaints = complaintService.searchSimilarComplaints(question, limit);
        log.info("Retrieved {} relevant complaints", relevantComplaints.size());
        
        // Step 2: Build context from retrieved complaints
        StringBuilder context = new StringBuilder();
        context.append("Here are relevant customer complaints from our database:\n\n");
        for (int i = 0; i < relevantComplaints.size(); i++) {
            Complaint c = relevantComplaints.get(i);
            context.append(String.format("[Complaint #%d]\n", i + 1));
            context.append(String.format("Customer: %s (%s)\n", c.getCustomerName(), c.getCountry()));
            context.append(String.format("Description: %s\n", c.getDescription()));
            context.append(String.format("Date: %s\n\n", c.getCreatedAt()));
        }
        
        // Step 3: Build prompt with RAG context
        String prompt = String.format("""
            You are a customer service AI assistant with access to a database of customer complaints.
            
            %s
            
            User Question: %s
            
            Based on the complaints provided above, answer the user's question in a helpful, accurate way.
            If you identify patterns, trends, or insights from the complaints, mention them.
            If the question cannot be fully answered from the provided complaints, say so and provide what you can.
            Be concise but thorough.
            """, context.toString(), question);
        
        log.info("Sending prompt to LLM with {} chars of context", context.length());
        
        // Step 4: Get LLM response with RAG context
        String response = getChatClient().prompt()
                .user(prompt)
                .call()
                .content();
        
        log.info("Received response from LLM: {} chars", response.length());
        
        return ResponseEntity.ok(Map.of(
            "answer", response,
            "retrievedComplaintsCount", relevantComplaints.size(),
            "relevantComplaints", relevantComplaints
        ));
    }
}
