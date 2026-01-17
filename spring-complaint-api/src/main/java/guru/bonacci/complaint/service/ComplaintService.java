package guru.bonacci.complaint.service;

import guru.bonacci.complaint.model.Complaint;
import guru.bonacci.complaint.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final EmbeddingModel embeddingModel;

    public List<Complaint> getAllComplaints() {
        return complaintRepository.findAll();
    }

    public Complaint getComplaintById(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found with id: " + id));
    }

    public Complaint saveComplaint(Complaint complaint) {
        log.info("Saving complaint: {}", complaint.getComplaintId());
        return complaintRepository.save(complaint);
    }

    public List<Complaint> searchSimilarComplaints(String searchQuery, int limit) {
        log.info("Searching for complaints similar to: {}", searchQuery);
        
        // Generate embedding for search query using Spring AI
        EmbeddingResponse response = embeddingModel.call(
            new EmbeddingRequest(List.of(searchQuery), null)
        );
        
        float[] embeddingArray = response.getResults().get(0).getOutput();
        log.info("Generated embedding with {} dimensions", embeddingArray.length);
        
        // Convert float[] to List<Float> for PostgreSQL vector format
        StringBuilder vectorString = new StringBuilder("[");
        for (int i = 0; i < embeddingArray.length; i++) {
            vectorString.append(embeddingArray[i]);
            if (i < embeddingArray.length - 1) {
                vectorString.append(",");
            }
        }
        vectorString.append("]");
        
        return complaintRepository.findSimilarComplaints(vectorString.toString(), limit);
    }
}
