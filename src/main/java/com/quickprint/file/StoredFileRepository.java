package com.quickprint.file;

import com.quickprint.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findByOwnerOrderByCreatedAtDesc(User owner);

    Optional<StoredFile> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    // Used by the cleanup job.
    List<StoredFile> findByExpiresAtBefore(Instant cutoff);

    // Atomic "spend one use": decrements only if a use is left. Returns the
    // number of rows changed (1 = a use was taken, 0 = already exhausted).
    // Doing it in one SQL statement means two concurrent receivers can never
    // both take the last download.
    @Modifying
    @Query("update StoredFile f set f.usesLeft = f.usesLeft - 1 where f.id = :id and f.usesLeft > 0")
    int decrementUse(@Param("id") Long id);
}
