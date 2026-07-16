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

    // File cap for free accounts. Counts everything the owner has; expired
    // files are removed hourly by CleanupJob, so the count is close enough.
    long countByOwner(User owner);

    // Used by the cleanup job: expired files, plus stragglers already at 0
    // uses (files now delete on their last use; this sweeps pre-existing rows).
    List<StoredFile> findByExpiresAtBeforeOrUsesLeft(Instant cutoff, Integer usesLeft);

    // Fresh value straight from the DB — after decrementUse the entity loaded
    // in this persistence context is stale.
    @Query("select f.usesLeft from StoredFile f where f.id = :id")
    Integer usesLeft(@Param("id") Long id);

    // Atomic "spend one use": decrements only if a use is left. Returns the
    // number of rows changed (1 = a use was taken, 0 = already exhausted).
    // Doing it in one SQL statement means two concurrent receivers can never
    // both take the last download.
    @Modifying
    @Query("update StoredFile f set f.usesLeft = f.usesLeft - 1 where f.id = :id and f.usesLeft > 0")
    int decrementUse(@Param("id") Long id);
}
