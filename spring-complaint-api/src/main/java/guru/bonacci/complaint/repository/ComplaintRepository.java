package guru.bonacci.complaint.repository;

import guru.bonacci.complaint.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    @Query(value = """
        SELECT c.* FROM complaints c
        JOIN complaint_embeddings ce ON c.complaint_id = ce.complaint_id
        ORDER BY ce.embedding <=> cast(:embedding as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Complaint> findSimilarComplaints(String embedding, int limit);
}
