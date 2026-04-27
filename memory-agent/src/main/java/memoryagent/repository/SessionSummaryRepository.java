package memoryagent.repository;

import memoryagent.entity.SessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionSummaryRepository extends JpaRepository<SessionSummary, Long> {}
