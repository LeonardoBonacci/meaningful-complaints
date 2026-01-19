package guru.bonacci.semantic;

import org.apache.flink.agents.api.Agent;
import org.apache.flink.agents.api.Action;
import org.apache.flink.agents.api.ChatModelSetup;
import org.apache.flink.agents.api.Prompt;
import org.apache.flink.agents.api.RunnerContext;
import org.apache.flink.agents.api.events.ChatRequestEvent;
import org.apache.flink.agents.api.events.ChatResponseEvent;
import org.apache.flink.agents.api.events.InputEvent;
import org.apache.flink.agents.api.events.OutputEvent;
import org.apache.flink.agents.api.prompt.ChatMessage;
import org.apache.flink.agents.api.prompt.MessageRole;
import org.apache.flink.agents.api.ResourceDescriptor;
import org.apache.flink.agents.ollama.OllamaChatModelSetup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An agent that uses a large language model (LLM) to analyze complaint sentiment and severity.
 *
 * <p>This agent receives a list of customer complaints for a country and time window,
 * then produces a sentiment analysis with severity assessment and key themes.
 */
public class ComplaintSentimentAgent extends Agent {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static final org.apache.flink.agents.api.prompt.Prompt SENTIMENT_ANALYSIS_PROMPT =
        org.apache.flink.agents.api.prompt.Prompt.Builder.newBuilder()
            .addSystemMessage("You are an expert customer service analyst specializing in complaint sentiment analysis.")
            .addUserMessage("Analyze the sentiment and severity of the following customer complaints. " +
                "Provide a brief summary of the overall complaint severity (Low, Medium, High, Critical) " +
                "and key themes. Keep your response concise (2-3 sentences).\n\n" +
                "Return your analysis in JSON format with these fields:\n" +
                "{\n" +
                "  \"severity\": \"Low|Medium|High|Critical\",\n" +
                "  \"themes\": [\"theme1\", \"theme2\"],\n" +
                "  \"summary\": \"Brief analysis summary\"\n" +
                "}\n\n" +
                "Complaints:\n{{input}}")
            .build();

    @Prompt
    public static org.apache.flink.agents.api.prompt.Prompt sentimentAnalysisPrompt() {
        return SENTIMENT_ANALYSIS_PROMPT;
    }

    @ChatModelSetup
    public static ResourceDescriptor sentimentAnalysisModel() {
        return ResourceDescriptor.Builder.newBuilder(OllamaChatModelSetup.class.getName())
                .addInitialArgument("connection", "ollamaChatModelConnection")
                .addInitialArgument("model", "llama3.2:latest")
                .addInitialArgument("prompt", "sentimentAnalysisPrompt")
                .addInitialArgument("extract_reasoning", "false")
                .build();
    }

    /**
     * Process input event containing windowed complaints and send chat request for sentiment analysis.
     */
    @Action(listenEvents = {InputEvent.class})
    public static void processInput(InputEvent event, RunnerContext ctx) throws Exception {
        String input = (String) event.getInput();
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ComplaintWindowInput inputObj = MAPPER.readValue(input, ComplaintWindowInput.class);

        // Store metadata in short-term memory
        ctx.getShortTermMemory().set("country", inputObj.getCountry());
        ctx.getShortTermMemory().set("windowStart", inputObj.getWindowStart());
        ctx.getShortTermMemory().set("windowEnd", inputObj.getWindowEnd());

        // Format complaints for LLM
        StringBuilder complaintsText = new StringBuilder();
        int count = 1;
        for (String complaint : inputObj.getComplaints()) {
            complaintsText.append(count++).append(". ").append(complaint).append("\n");
        }

        ChatMessage msg = new ChatMessage(
            MessageRole.USER, 
            "", 
            Map.of("input", complaintsText.toString())
        );

        ctx.sendEvent(new ChatRequestEvent("sentimentAnalysisModel", List.of(msg)));
    }

    /**
     * Process LLM response and emit structured output.
     */
    @Action(listenEvents = ChatResponseEvent.class)
    public static void processChatResponse(ChatResponseEvent event, RunnerContext ctx) throws Exception {
        String content = event.getResponse().getContent();
        
        JsonNode jsonNode = MAPPER.readTree(content);
        String severity = jsonNode.has("severity") ? jsonNode.get("severity").asText() : "Unknown";
        String summary = jsonNode.has("summary") ? jsonNode.get("summary").asText() : content;
        
        List<String> themes = new ArrayList<>();
        if (jsonNode.has("themes") && jsonNode.get("themes").isArray()) {
            for (JsonNode node : jsonNode.get("themes")) {
                themes.add(node.asText());
            }
        }

        String country = ctx.getShortTermMemory().get("country").getValue().toString();
        long windowStart = Long.parseLong(ctx.getShortTermMemory().get("windowStart").getValue().toString());
        long windowEnd = Long.parseLong(ctx.getShortTermMemory().get("windowEnd").getValue().toString());

        SentimentAnalysisResult result = new SentimentAnalysisResult(
            country, windowStart, windowEnd, severity, themes, summary
        );

        System.out.println(String.format("\n=== Sentiment Analysis for %s ===", country));
        System.out.println(String.format("Window: [%d - %d]", windowStart, windowEnd));
        System.out.println(String.format("Severity: %s", severity));
        System.out.println(String.format("Themes: %s", String.join(", ", themes)));
        System.out.println(String.format("Summary: %s\n", summary));

        ctx.sendEvent(new OutputEvent(result));
    }

    /**
     * Input POJO for windowed complaints
     */
    public static class ComplaintWindowInput implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private String country;
        private long windowStart;
        private long windowEnd;
        private List<String> complaints;

        public ComplaintWindowInput() {}

        public ComplaintWindowInput(String country, long windowStart, long windowEnd, List<String> complaints) {
            this.country = country;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.complaints = complaints;
        }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
        
        public long getWindowStart() { return windowStart; }
        public void setWindowStart(long windowStart) { this.windowStart = windowStart; }
        
        public long getWindowEnd() { return windowEnd; }
        public void setWindowEnd(long windowEnd) { this.windowEnd = windowEnd; }
        
        public List<String> getComplaints() { return complaints; }
        public void setComplaints(List<String> complaints) { this.complaints = complaints; }
    }

    /**
     * Output POJO for sentiment analysis result
     */
    public static class SentimentAnalysisResult implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        public String country;
        public long windowStart;
        public long windowEnd;
        public String severity;
        public List<String> themes;
        public String summary;

        public SentimentAnalysisResult() {}

        public SentimentAnalysisResult(String country, long windowStart, long windowEnd, 
                                      String severity, List<String> themes, String summary) {
            this.country = country;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.severity = severity;
            this.themes = themes;
            this.summary = summary;
        }

        @Override
        public String toString() {
            return String.format("SentimentAnalysisResult{country=%s, window=[%d-%d], severity=%s, themes=%s, summary='%s'}",
                country, windowStart, windowEnd, severity, String.join(",", themes), 
                summary.length() > 100 ? summary.substring(0, 100) + "..." : summary);
        }
    }
}
