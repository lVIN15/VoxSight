package edu.cit.capstone.voxsight.repository;

import edu.cit.capstone.voxsight.model.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link PracticeSession}.
 *
 * Provides CRUD + the two history queries the client will need once
 * a user profile screen is implemented.
 */
@Repository
public interface SessionRepository extends JpaRepository<PracticeSession, UUID> {

    /**
     * Returns all sessions for a given user, ordered most-recent first.
     * Used by the future "My Progress" screen.
     */
    List<PracticeSession> findByUserIdOrderByStartedAtDesc(UUID userId);

    /**
     * Returns all sessions that used a particular score.
     * Useful for score-level analytics.
     */
    List<PracticeSession> findByScoreId(UUID scoreId);
}
