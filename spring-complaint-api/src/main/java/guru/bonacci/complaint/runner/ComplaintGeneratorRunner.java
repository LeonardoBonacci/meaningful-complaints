package guru.bonacci.complaint.runner;

import guru.bonacci.complaint.model.Complaint;
import guru.bonacci.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComplaintGeneratorRunner implements CommandLineRunner {

    private final ComplaintRepository complaintRepository;
    
    private static final List<String> COMPLAINTS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    
    // Hardcoded users per country
    private static final Map<String, List<String>> USERS_BY_COUNTRY = Map.of(
            "Belgium", List.of(
                    "Pieter Van Den Berg", "Sophie Dubois", "Marc Janssens", "Emma Claes",
                    "Lucas Peeters", "Marie Leroy", "Thomas Mertens", "Julie De Smet",
                    "Nicolas Lambert", "Laura Willems"
            ),
            "Mexico", List.of(
                    "Carlos Rodriguez", "Maria Gonzalez", "Juan Martinez", "Ana Lopez",
                    "Luis Hernandez", "Sofia Garcia", "Miguel Sanchez", "Carmen Ramirez",
                    "Diego Torres", "Isabella Flores"
            ),
            "Japan", List.of(
                    "Hiroshi Tanaka", "Yuki Yamamoto", "Takeshi Sato", "Sakura Suzuki",
                    "Kenji Watanabe", "Aiko Ito", "Ryo Kobayashi", "Hana Nakamura",
                    "Daichi Kato", "Mei Yoshida"
            )
    );
    
    private static final List<String> COUNTRIES = List.of("Belgium", "Mexico", "Japan");

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting Complaint Generator Runner...");
        
        // Load complaints from file
        loadComplaints();
        
        if (COMPLAINTS.isEmpty()) {
            log.error("No complaints loaded from file. Exiting runner.");
            return;
        }
        
        log.info("Loaded {} complaints from file", COMPLAINTS.size());
        log.info("Will generate random complaints every 3 seconds...");
        
        // Create scheduled executor to run every 3 seconds
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                this::generateAndSaveComplaint,
                0, // initial delay
                3, // period
                TimeUnit.SECONDS
        );
        
        log.info("Complaint generator scheduled successfully");
    }
    
    private void loadComplaints() {
        try {
            ClassPathResource resource = new ClassPathResource("complaints-data.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        COMPLAINTS.add(line);
                    }
                }
            }
            log.info("Successfully loaded {} complaints from complaints-data.txt", COMPLAINTS.size());
        } catch (Exception e) {
            log.error("Failed to load complaints from file", e);
        }
    }
    
    private void generateAndSaveComplaint() {
        try {
            // Pick random country
            String country = COUNTRIES.get(RANDOM.nextInt(COUNTRIES.size()));
            
            // Pick random user from that country
            List<String> users = USERS_BY_COUNTRY.get(country);
            String customerName = users.get(RANDOM.nextInt(users.size()));
            
            // Pick random complaint description
            String description = COMPLAINTS.get(RANDOM.nextInt(COMPLAINTS.size()));
            
            // Create and save complaint
            Complaint complaint = new Complaint();
            complaint.setCustomerName(customerName);
            complaint.setCountry(country);
            complaint.setDescription(description);
            complaint.setCreatedAt(LocalDateTime.now());
            
            Complaint saved = complaintRepository.save(complaint);
            
            log.info("Generated complaint #{} - {} from {} - {}...", 
                    saved.getComplaintId(), 
                    customerName, 
                    country,
                    description.substring(0, Math.min(50, description.length())));
            
        } catch (Exception e) {
            log.error("Error generating complaint", e);
        }
    }
}
