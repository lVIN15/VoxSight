package edu.cit.capstone.voxsight.repository;

import edu.cit.capstone.voxsight.model.PitchAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link PitchAttempt}.
 */
@Repository
public interface PitchAttemptRepository extends JpaRepository<PitchAttempt, UUID> {

    /**
     * Returns all attempt records for a given session.
     * Used by [SessionMetricsController] to compute the aggregated
     * performance score if individual attempts were already saved.
     */
    List<PitchAttempt> findBySessionId(UUID sessionId);

    /**
     * Counts only the matched attempts for a session — handy for computing
     * accuracy without fetching all rows.
     */
    long countBySessionIdAndMatchTrue(UUID sessionId);
}
