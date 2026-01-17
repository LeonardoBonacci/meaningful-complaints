package guru.bonacci.complaint.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;

    private ChatClient getChatClient() {
        return chatClientBuilder.build();
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, String>> analyzeComplaint(@RequestBody Map<String, String> request) {
        String complaintText = request.get("complaint");
        
        String prompt = String.format("""
            Analyze the following customer complaint and provide:
            1. Sentiment (positive/negative/neutral)
            2. Category (billing/technical/service/other)
            3. Urgency (low/medium/high)
            4. Brief summary
            
            Complaint: %s
            
            Respond in a structured format.
            """, complaintText);
        
        String response = getChatClient().prompt()
                .user(prompt)
                .call()
                .content();
        
        return ResponseEntity.ok(Map.of("analysis", response));
    }

    @PostMapping("/suggest-response")
    public ResponseEntity<Map<String, String>> suggestResponse(@RequestBody Map<String, String> request) {
        String complaintText = request.get("complaint");
        
        String prompt = String.format("""
            Generate a professional customer service response to this complaint:
            
            %s
            
            The response should be empathetic, offer a solution, and maintain a professional tone.
            """, complaintText);
        
        String response = getChatClient().prompt()
                .user(prompt)
                .call()
                .content();
        
        return ResponseEntity.ok(Map.of("suggestedResponse", response));
    }
}
