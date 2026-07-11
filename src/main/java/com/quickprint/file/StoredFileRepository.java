package com.quickprint.file;

import com.quickprint.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findByOwnerOrderByCreatedAtDesc(User owner);

    Optional<StoredFile> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    // Used by the cleanup job.
    List<StoredFile> findByExpiresAtBefore(Instant cutoff);
}
