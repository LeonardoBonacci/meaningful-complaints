package guru.bonacci.complaint.controller;

import guru.bonacci.complaint.model.Complaint;
import guru.bonacci.complaint.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @GetMapping
    public ResponseEntity<List<Complaint>> getAllComplaints() {
        return ResponseEntity.ok(complaintService.getAllComplaints());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Complaint> getComplaintById(@PathVariable Long id) {
        return ResponseEntity.ok(complaintService.getComplaintById(id));
    }

    @PostMapping
    public ResponseEntity<Complaint> addComplaint(@RequestBody Complaint complaint) {
        Complaint saved = complaintService.saveComplaint(complaint);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Complaint>> searchSimilarComplaints(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        List<Complaint> similar = complaintService.searchSimilarComplaints(query, limit);
        return ResponseEntity.ok(similar);
    }
}
