# Guest Mode (Modo Convidado) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Anonymous ("guest") one-time file sending — generated friendly nickname + fixed 6-char code, image/PDF only, 24h TTL — plus tier limits on account uploads (20 active files, light types only).

**Architecture:** Same `StoredFile` table: `owner` becomes nullable (null = guest) and a nullable `fixedCode` column drives a validation branch in `ReceiveService` (fixed code → constant-time compare; otherwise rolling TOTP as today). A public `POST /api/guest` endpoint (rate-limited, CSRF-exempt) creates guest files. Type limits are enforced by magic-bytes detection, never the browser's content-type.

**Tech Stack:** Java 21, Spring Boot, Spring Security, PostgreSQL (Hibernate `ddl-auto=update`), JUnit 5 + AssertJ + Mockito (all via `spring-boot-starter-test`), plain HTML/CSS/JS frontend in `src/main/resources/static`. Maven via wrapper.

**Spec:** `docs/superpowers/specs/2026-07-16-guest-mode-design.md`

## Global Constraints

- Base package `com.quickprint`. No Lombok — explicit code. No new dependencies.
- Comments in English (codebase convention). Deliberate shortcuts get a `ponytail:` comment naming the ceiling.
- Build/test on Windows PowerShell: `.\mvnw.cmd test` (quote `-D` args: `.\mvnw.cmd test "-Dtest=FooTest"`).
- Static frontend files are copied at boot — restart `spring-boot:run` to see changes.
- Error contracts: guest upload → 400 empty, 413 too big (multipart config), 415 type not allowed, 429 rate-limited, 201 `{nickname, code}`. Account upload additionally → 409 file cap. Receive stays generic 404.
- Existing tests must keep passing: `AccessCodesTest`, `ReceiveRateLimiterTest`.
- Commit style: short imperative subject (e.g. "Add file deletion (owner-only)"), ending with the Claude co-author line.

## File Structure

| File | Responsibility |
|---|---|
| Create `src/main/java/com/quickprint/file/FixedCodes.java` | Generate + constant-time-validate fixed 6-char codes |
| Create `src/main/java/com/quickprint/file/FileTypePolicy.java` | Magic-bytes detection + per-tier allowed kinds |
| Create `src/main/java/com/quickprint/file/UnsupportedFileTypeException.java` | 415 |
| Create `src/main/java/com/quickprint/file/FileLimitReachedException.java` | 409 |
| Create `src/main/java/com/quickprint/guest/NicknameGenerator.java` | `adjective+animal+2digits` names |
| Create `src/main/java/com/quickprint/guest/GuestUploadRateLimiter.java` | 5 uploads / IP / hour |
| Create `src/main/java/com/quickprint/guest/GuestController.java` | `POST /api/guest` |
| Modify `src/main/java/com/quickprint/file/StoredFile.java` | nullable owner, `fixedCode`, guest factory |
| Modify `src/main/java/com/quickprint/file/StoredFileRepository.java` | `countByOwner` |
| Modify `src/main/java/com/quickprint/file/FileService.java` | `uploadGuest`, cap + type gating |
| Modify `src/main/java/com/quickprint/receive/ReceiveService.java` | fixed-code branch |
| Modify `src/main/java/com/quickprint/config/SecurityConfig.java` | permit + CSRF-exempt `/api/guest` |
| Modify `src/main/resources/application.properties` | `guest-ttl`, `free-max-files` |
| Modify `src/main/resources/static/index.html` + create `js/guest.js` + modify `css/styles.css` | guest send flow |
| Tests under `src/test/java/com/quickprint/...` | one test class per new unit |

---

### Task 1: FixedCodes

**Files:**
- Create: `src/main/java/com/quickprint/file/FixedCodes.java`
- Test: `src/test/java/com/quickprint/file/FixedCodesTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `FixedCodes.generate() -> String` (6 chars, alphabet `ABCDEFGHJKMNPQRSTUVWXYZ23456789`); `FixedCodes.matches(String stored, String submitted) -> boolean` (null-safe, trims + uppercases `submitted`, constant-time).

- [ ] **Step 1: Write the failing test**

```java
package com.quickprint.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedCodesTest {

    @Test
    void generatesSixCharsFromTheUnambiguousAlphabet() {
        assertThat(FixedCodes.generate())
                .hasSize(6)
                .matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void matchesNormalizesCaseAndWhitespace() {
        assertThat(FixedCodes.matches("ABC234", "  abc234  ")).isTrue();
        assertThat(FixedCodes.matches("ABC234", "ZZZZZZ")).isFalse();
        assertThat(FixedCodes.matches("ABC234", null)).isFalse();
    }

    @Test
    void twoGeneratedCodesAlmostAlwaysDiffer() {
        assertThat(FixedCodes.generate()).isNotEqualTo(FixedCodes.generate());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=FixedCodesTest"`
Expected: COMPILATION ERROR — `FixedCodes` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.quickprint.file;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

// Fixed access codes for guest uploads. Unlike the rolling AccessCodes used by
// account files, a guest file's code never changes: it is generated once,
// stored on the file, and compared in constant time. Guest files are one-time
// use with a short TTL, which keeps the fixed code's exposure window small.
public final class FixedCodes {

    // Same unambiguous alphabet as AccessCodes (no 0/O, 1/I/L).
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private FixedCodes() {
    }

    public static String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    // Constant-time compare so a timing side-channel can't reveal the code.
    public static boolean matches(String stored, String submitted) {
        if (submitted == null) {
            return false;
        }
        String normalized = submitted.trim().toUpperCase();
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                normalized.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=FixedCodesTest"`
Expected: `Tests run: 3, Failures: 0` — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/quickprint/file/FixedCodes.java src/test/java/com/quickprint/file/FixedCodesTest.java
git commit -m "Add fixed access codes for guest files"
```

---

### Task 2: NicknameGenerator

**Files:**
- Create: `src/main/java/com/quickprint/guest/NicknameGenerator.java`
- Test: `src/test/java/com/quickprint/guest/NicknameGeneratorTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `NicknameGenerator.generate() -> String` — `adjective+animal+2digits` (e.g. `sillygoose76`), lowercase, always matches the existing nickname slug `[a-z0-9-]{1,20}`.

- [ ] **Step 1: Write the failing test**

```java
package com.quickprint.guest;

import org.junit.jupiter.api.RepeatedTest;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameGeneratorTest {

    // Run many times: every generated name must fit the nickname slug rules
    // FileService enforces ([a-z0-9-]{1,20}) and end in two digits.
    @RepeatedTest(200)
    void alwaysProducesAValidSlugEndingInTwoDigits() {
        String name = NicknameGenerator.generate();

        assertThat(name).matches("[a-z]+[0-9]{2}");
        assertThat(name.length()).isLessThanOrEqualTo(20);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=NicknameGeneratorTest"`
Expected: COMPILATION ERROR — `NicknameGenerator` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.quickprint.guest;

import java.security.SecureRandom;

// Friendly names for guest uploads: adjective + animal + 2 digits, e.g.
// "sillygoose76". Words are all <= 6 chars so the result always fits the
// 20-char nickname slug. Uniqueness is the caller's job (retry on collision).
public final class NicknameGenerator {

    private static final String[] ADJECTIVES = {
            "silly", "happy", "brave", "calm", "sunny", "witty", "fuzzy",
            "jolly", "lucky", "merry", "noble", "perky", "quick", "shiny",
            "zesty", "bouncy", "chirpy", "dandy", "eager", "frosty",
    };

    private static final String[] ANIMALS = {
            "goose", "otter", "panda", "tiger", "koala", "gecko", "llama",
            "moose", "robin", "shark", "whale", "zebra", "bison", "camel",
            "dingo", "eagle", "ferret", "heron", "iguana", "jackal",
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private NicknameGenerator() {
    }

    public static String generate() {
        return ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)]
                + ANIMALS[RANDOM.nextInt(ANIMALS.length)]
                + (10 + RANDOM.nextInt(90));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=NicknameGeneratorTest"`
Expected: `Tests run: 200, Failures: 0` — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/quickprint/guest/NicknameGenerator.java src/test/java/com/quickprint/guest/NicknameGeneratorTest.java
git commit -m "Add friendly nickname generator for guest uploads"
```

---

### Task 3: FileTypePolicy (magic bytes)

**Files:**
- Create: `src/main/java/com/quickprint/file/FileTypePolicy.java`
- Test: `src/test/java/com/quickprint/file/FileTypePolicyTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `FileTypePolicy.Kind` enum (`PNG, JPEG, GIF, WEBP, PDF, OFFICE_DOC, ZIP, UNKNOWN`); `FileTypePolicy.detect(byte[] head) -> Kind` (expects the first 64 bytes); `FileTypePolicy.allowedForGuest(Kind) -> boolean` (images + PDF); `FileTypePolicy.allowedForFreeAccount(Kind) -> boolean` (images + PDF + OFFICE_DOC).

- [ ] **Step 1: Write the failing test**

```java
package com.quickprint.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypePolicyTest {

    private static byte[] head(int... bytes) {
        byte[] out = new byte[64];
        for (int i = 0; i < bytes.length; i++) {
            out[i] = (byte) bytes[i];
        }
        return out;
    }

    @Test
    void detectsCommonTypesByMagicBytes() {
        assertThat(FileTypePolicy.detect(head(0x89, 'P', 'N', 'G'))).isEqualTo(FileTypePolicy.Kind.PNG);
        assertThat(FileTypePolicy.detect(head(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(FileTypePolicy.Kind.JPEG);
        assertThat(FileTypePolicy.detect(head('G', 'I', 'F', '8'))).isEqualTo(FileTypePolicy.Kind.GIF);
        assertThat(FileTypePolicy.detect(head('%', 'P', 'D', 'F'))).isEqualTo(FileTypePolicy.Kind.PDF);
    }

    @Test
    void detectsWebpInsideRiff() {
        byte[] webp = head('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
        assertThat(FileTypePolicy.detect(webp)).isEqualTo(FileTypePolicy.Kind.WEBP);
    }

    @Test
    void distinguishesOfficeDocsFromPlainZips() {
        byte[] docx = head('P', 'K', 3, 4);
        byte[] marker = "[Content_Types].xml".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, docx, 30, marker.length);
        byte[] zip = head('P', 'K', 3, 4);

        assertThat(FileTypePolicy.detect(docx)).isEqualTo(FileTypePolicy.Kind.OFFICE_DOC);
        assertThat(FileTypePolicy.detect(zip)).isEqualTo(FileTypePolicy.Kind.ZIP);
    }

    @Test
    void renamedExecutableIsUnknownRegardlessOfDeclaredType() {
        // "MZ" — a Windows .exe renamed to .pdf still starts with MZ.
        assertThat(FileTypePolicy.detect(head('M', 'Z', 0x90, 0)))
                .isEqualTo(FileTypePolicy.Kind.UNKNOWN);
        assertThat(FileTypePolicy.detect(null)).isEqualTo(FileTypePolicy.Kind.UNKNOWN);
        assertThat(FileTypePolicy.detect(Arrays.copyOf(new byte[0], 2)))
                .isEqualTo(FileTypePolicy.Kind.UNKNOWN);
    }

    @Test
    void guestAllowsOnlyImagesAndPdf() {
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.PNG)).isTrue();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.PDF)).isTrue();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.OFFICE_DOC)).isFalse();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.ZIP)).isFalse();
        assertThat(FileTypePolicy.allowedForGuest(FileTypePolicy.Kind.UNKNOWN)).isFalse();
    }

    @Test
    void freeAccountAlsoAllowsOfficeDocsButNotZips() {
        assertThat(FileTypePolicy.allowedForFreeAccount(FileTypePolicy.Kind.OFFICE_DOC)).isTrue();
        assertThat(FileTypePolicy.allowedForFreeAccount(FileTypePolicy.Kind.ZIP)).isFalse();
        assertThat(FileTypePolicy.allowedForFreeAccount(FileTypePolicy.Kind.UNKNOWN)).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=FileTypePolicyTest"`
Expected: COMPILATION ERROR — `FileTypePolicy` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.quickprint.file;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

// What kind of file is this REALLY? The browser's content-type is caller
// -controlled, so tier rules ("guests may only send images/PDF") are enforced
// on the first bytes of the actual content instead.
// ponytail: tiny hand-rolled signature check for the handful of types we gate
// on; switch to Apache Tika only if the matrix ever grows.
public final class FileTypePolicy {

    public enum Kind { PNG, JPEG, GIF, WEBP, PDF, OFFICE_DOC, ZIP, UNKNOWN }

    // Video and ZIP stay out of both sets — they are the Pro tier's hook.
    private static final Set<Kind> GUEST = EnumSet.of(Kind.PNG, Kind.JPEG, Kind.GIF, Kind.WEBP, Kind.PDF);
    private static final Set<Kind> FREE = EnumSet.of(Kind.PNG, Kind.JPEG, Kind.GIF, Kind.WEBP, Kind.PDF, Kind.OFFICE_DOC);

    /** How many leading bytes callers should hand to {@link #detect}. */
    public static final int HEAD_LENGTH = 64;

    private FileTypePolicy() {
    }

    public static Kind detect(byte[] head) {
        if (head == null || head.length < 4) {
            return Kind.UNKNOWN;
        }
        if (starts(head, 0x89, 'P', 'N', 'G')) {
            return Kind.PNG;
        }
        if (starts(head, 0xFF, 0xD8, 0xFF)) {
            return Kind.JPEG;
        }
        if (starts(head, 'G', 'I', 'F', '8')) {
            return Kind.GIF;
        }
        if (starts(head, 'R', 'I', 'F', 'F') && head.length >= 12
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Kind.WEBP;
        }
        if (starts(head, '%', 'P', 'D', 'F')) {
            return Kind.PDF;
        }
        if (starts(head, 'P', 'K', 3, 4)) {
            // Office files (docx/xlsx/pptx) are zips whose first entry is
            // [Content_Types].xml — its name sits in the first local header.
            String ascii = new String(head, StandardCharsets.US_ASCII);
            return ascii.contains("[Content_Types].xml") ? Kind.OFFICE_DOC : Kind.ZIP;
        }
        return Kind.UNKNOWN;
    }

    public static boolean allowedForGuest(Kind kind) {
        return GUEST.contains(kind);
    }

    public static boolean allowedForFreeAccount(Kind kind) {
        return FREE.contains(kind);
    }

    private static boolean starts(byte[] head, int... signature) {
        for (int i = 0; i < signature.length; i++) {
            if (head[i] != (byte) signature[i]) {
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=FileTypePolicyTest"`
Expected: `Tests run: 6, Failures: 0` — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/quickprint/file/FileTypePolicy.java src/test/java/com/quickprint/file/FileTypePolicyTest.java
git commit -m "Add magic-bytes file type policy per tier"
```

---

### Task 4: StoredFile — nullable owner + fixedCode + guest factory

**Files:**
- Modify: `src/main/java/com/quickprint/file/StoredFile.java`
- Modify: `src/main/java/com/quickprint/file/StoredFileRepository.java`
- Manual: SQL in pgAdmin (see Step 3)

**Interfaces:**
- Consumes: `AccessCodes.newSecret()` (existing), `FixedCodes` naming only (codes are passed in as `String`).
- Produces: `StoredFile.guest(String originalFilename, String nickname, String storageName, String contentType, long sizeBytes, String fixedCode, Instant expiresAt) -> StoredFile` (owner null, oneTime true, shareLimit/usesLeft 1); `storedFile.getFixedCode() -> String|null`; `StoredFileRepository.countByOwner(User owner) -> long`.

- [ ] **Step 1: Relax the owner mapping and add fixedCode**

In `StoredFile.java`, change the owner mapping (lines ~27-29) from:

```java
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
```

to:

```java
    // Null owner = guest upload (anonymous, one-time, fixed code).
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;
```

After the `secret` field (line ~57), add:

```java
    // Fixed access code for guest files; null for account files, whose codes
    // roll (see AccessCodes). Presence of this value picks the validation path.
    @Column(name = "fixed_code")
    private String fixedCode;
```

After the existing constructor (line ~87), add the guest factory and getter:

```java
    // A guest upload: no owner, one-time use, fixed code, short TTL. A rolling
    // secret is still generated so the column's NOT NULL stays untouched — it
    // is simply never used when fixedCode is present.
    public static StoredFile guest(String originalFilename, String nickname, String storageName,
                                   String contentType, long sizeBytes, String fixedCode,
                                   Instant expiresAt) {
        StoredFile file = new StoredFile(null, originalFilename, nickname, storageName,
                contentType, sizeBytes, 1, true, AccessCodes.newSecret(), expiresAt);
        file.fixedCode = fixedCode;
        return file;
    }
```

and with the other getters:

```java
    public String getFixedCode() {
        return fixedCode;
    }
```

- [ ] **Step 2: Add the repository count for the file cap**

In `StoredFileRepository.java`, add below `existsByNickname`:

```java
    // File cap for free accounts. Counts everything the owner has; expired
    // files are removed hourly by CleanupJob, so the count is close enough.
    long countByOwner(User owner);
```

- [ ] **Step 3: Manual DB migration (pgAdmin)**

`ddl-auto=update` adds the new nullable `fixed_code` column automatically but
**never drops an existing NOT NULL**. In pgAdmin: right-click the `quickprint`
database → Query Tool → run:

```sql
ALTER TABLE stored_files ALTER COLUMN owner_id DROP NOT NULL;
```

Expected: `ALTER TABLE` success message.

- [ ] **Step 4: Run the full test suite (regression check)**

Run: `.\mvnw.cmd test`
Expected: all existing tests still pass (`AccessCodesTest`, `ReceiveRateLimiterTest`, plus Tasks 1–3) — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/quickprint/file/StoredFile.java src/main/java/com/quickprint/file/StoredFileRepository.java
git commit -m "Allow ownerless guest files with a fixed access code"
```

---

### Task 5: ReceiveService — fixed-code validation branch

**Files:**
- Modify: `src/main/java/com/quickprint/receive/ReceiveService.java`
- Test: `src/test/java/com/quickprint/receive/ReceiveServiceGuestTest.java`

**Interfaces:**
- Consumes: `StoredFile.guest(...)` and `getFixedCode()` (Task 4), `FixedCodes.matches(stored, submitted)` (Task 1).
- Produces: unchanged public API — `receiveService.fetch(nickname, code, preview)` now accepts a guest file's fixed code.

- [ ] **Step 1: Write the failing test**

```java
package com.quickprint.receive;

import com.quickprint.file.FileStorageService;
import com.quickprint.file.StoredFile;
import com.quickprint.file.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiveServiceGuestTest {

    private static StoredFile guestFile() {
        return StoredFile.guest("doc.pdf", "sillygoose76", "blob", "application/pdf",
                5, "ABC234", Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    void guestFileIsServedWithItsFixedCodeAndDeletedAfterUse(@TempDir Path dir) throws IOException {
        Path stored = dir.resolve("blob");
        Files.write(stored, "hello".getBytes());

        StoredFile file = guestFile();
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.findByNickname("sillygoose76")).thenReturn(Optional.of(file));
        when(repo.decrementUse(any())).thenReturn(1);
        when(storage.pathFor("blob")).thenReturn(stored);

        ReceiveService service = new ReceiveService(repo, storage);
        ReceiveService.Fetched fetched = service.fetch("sillygoose76", "abc234", false);

        assertThat(fetched.content()).isEqualTo("hello".getBytes());
        // one-time file: gone after the single use
        verify(storage).delete("blob");
        verify(repo).delete(file);
    }

    @Test
    void guestFileRejectsAWrongCodeWithTheGenericNotFound() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.findByNickname("sillygoose76")).thenReturn(Optional.of(guestFile()));

        ReceiveService service = new ReceiveService(repo, storage);

        assertThatThrownBy(() -> service.fetch("sillygoose76", "ZZZZZZ", false))
                .isInstanceOf(DownloadNotAvailableException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=ReceiveServiceGuestTest"`
Expected: FAIL — both tests throw `DownloadNotAvailableException` on the valid code too, because `fetch` still validates via `AccessCodes` only.

- [ ] **Step 3: Implement the branch**

In `ReceiveService.java`, add the import:

```java
import com.quickprint.file.FixedCodes;
```

Change the lookup in `fetch` (lines ~33-36) from:

```java
        StoredFile file = files.findByNickname(nickname)
                .filter(f -> !f.isExpired())
                .filter(f -> AccessCodes.isValid(f.getSecret(), code))
                .orElseThrow(DownloadNotAvailableException::new);
```

to:

```java
        StoredFile file = files.findByNickname(nickname)
                .filter(f -> !f.isExpired())
                .filter(f -> codeMatches(f, code))
                .orElseThrow(DownloadNotAvailableException::new);
```

and add the private helper at the bottom of the class:

```java
    // Guest files carry a fixed code; account files derive a rolling one.
    private static boolean codeMatches(StoredFile file, String code) {
        if (file.getFixedCode() != null) {
            return FixedCodes.matches(file.getFixedCode(), code);
        }
        return AccessCodes.isValid(file.getSecret(), code);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd test`
Expected: `ReceiveServiceGuestTest` passes and the full suite stays green — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/quickprint/receive/ReceiveService.java src/test/java/com/quickprint/receive/ReceiveServiceGuestTest.java
git commit -m "Accept fixed guest codes on the receive path"
```

---

### Task 6: FileService.uploadGuest + config

**Files:**
- Modify: `src/main/java/com/quickprint/file/FileService.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/quickprint/file/FileServiceGuestUploadTest.java`

**Interfaces:**
- Consumes: `NicknameGenerator.generate()` (Task 2), `FixedCodes.generate()` (Task 1), `FileTypePolicy.detect/allowedForGuest` (Task 3), `StoredFile.guest(...)` (Task 4), existing `FileStorageService.store(MultipartFile) -> String`.
- Produces: `FileService.GuestUpload(String nickname, String code)` record; `fileService.uploadGuest(MultipartFile) -> GuestUpload`. New constructor signature: `FileService(StoredFileRepository, FileStorageService, Duration defaultTtl, Duration guestTtl, int freeMaxFiles)`.

- [ ] **Step 1: Add the config keys**

In `application.properties`, under the `--- QuickPrint storage ---` section add:

```properties
# Guest (anonymous) uploads live much shorter than account uploads.
quickprint.storage.guest-ttl=PT24H
# Max active files per free account.
quickprint.limits.free-max-files=20
```

- [ ] **Step 2: Write the failing test**

```java
package com.quickprint.file;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceGuestUploadTest {

    private static final byte[] PDF_HEAD = "%PDF-1.7 minimal".getBytes();

    private FileService service(StoredFileRepository repo, FileStorageService storage) {
        return new FileService(repo, storage, Duration.ofDays(7), Duration.ofHours(24), 20);
    }

    @Test
    void guestUploadReturnsGeneratedNicknameAndCode() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        when(repo.existsByNickname(anyString())).thenReturn(false);
        when(repo.save(any(StoredFile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.store(any())).thenReturn("uuid-on-disk");

        FileService.GuestUpload result = service(repo, storage)
                .uploadGuest(new MockMultipartFile("file", "cv.pdf", "application/pdf", PDF_HEAD));

        assertThat(result.nickname()).matches("[a-z]+[0-9]{2}");
        assertThat(result.code()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}");
    }

    @Test
    void guestUploadRejectsNonImageNonPdfContent() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        // A Windows executable renamed to .pdf: content wins, not the name.
        MockMultipartFile exe = new MockMultipartFile(
                "file", "totally-a.pdf", "application/pdf", new byte[]{'M', 'Z', 0x90, 0});

        assertThatThrownBy(() -> service(repo, storage).uploadGuest(exe))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void guestUploadRejectsAnEmptyFile() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        FileStorageService storage = mock(FileStorageService.class);
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service(repo, storage).uploadGuest(empty))
                .isInstanceOf(InvalidUploadException.class);
    }
}
```

Also create the 415 exception this test needs, `src/main/java/com/quickprint/file/UnsupportedFileTypeException.java`:

```java
package com.quickprint.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// The uploaded bytes are not a type this tier may send (checked by magic
// bytes, so renaming a file doesn't help).
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException() {
        super("This file type is not allowed here. Allowed: images and PDF.");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=FileServiceGuestUploadTest"`
Expected: COMPILATION ERROR — no `uploadGuest`, no `GuestUpload`, constructor mismatch.

- [ ] **Step 4: Implement uploadGuest**

In `FileService.java`:

1. Add imports:

```java
import com.quickprint.guest.NicknameGenerator;
import java.io.InputStream;
```

2. Replace the constructor and fields (lines ~24-33) with:

```java
    private final StoredFileRepository files;
    private final FileStorageService storage;
    private final Duration defaultTtl;
    private final Duration guestTtl;
    private final int freeMaxFiles;

    public FileService(StoredFileRepository files, FileStorageService storage,
                       @Value("${quickprint.storage.default-ttl}") Duration defaultTtl,
                       @Value("${quickprint.storage.guest-ttl}") Duration guestTtl,
                       @Value("${quickprint.limits.free-max-files}") int freeMaxFiles) {
        this.files = files;
        this.storage = storage;
        this.defaultTtl = defaultTtl;
        this.guestTtl = guestTtl;
        this.freeMaxFiles = freeMaxFiles;
    }
```

3. Add below the existing `upload` method:

```java
    // Anonymous one-time upload: generated name + fixed code, images/PDF only,
    // short TTL. Returns exactly what the sender must hand to the receiver.
    public record GuestUpload(String nickname, String code) {
    }

    @Transactional
    public GuestUpload uploadGuest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("File is empty.");
        }
        FileTypePolicy.Kind kind = FileTypePolicy.detect(headOf(file));
        if (!FileTypePolicy.allowedForGuest(kind)) {
            throw new UnsupportedFileTypeException();
        }

        String nickname = freshGuestNickname();
        String code = FixedCodes.generate();

        String storageName = storage.store(file);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String originalName = file.getOriginalFilename();
        originalName = originalName == null ? "file" : originalName.replaceAll(".*[/\\\\]", "");

        files.save(StoredFile.guest(originalName, nickname, storageName, contentType,
                file.getSize(), code, Instant.now().plus(guestTtl)));
        return new GuestUpload(nickname, code);
    }

    // The generator's space is ~36k names; retry a few times on collision.
    private String freshGuestNickname() {
        for (int attempt = 0; attempt < 30; attempt++) {
            String candidate = NicknameGenerator.generate();
            if (!files.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find a free guest nickname.");
    }

    // First bytes of the real content — what FileTypePolicy decides on.
    private static byte[] headOf(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(FileTypePolicy.HEAD_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\mvnw.cmd test`
Expected: `FileServiceGuestUploadTest` green, full suite green — BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/quickprint/file/FileService.java src/main/java/com/quickprint/file/UnsupportedFileTypeException.java src/main/resources/application.properties src/test/java/com/quickprint/file/FileServiceGuestUploadTest.java
git commit -m "Add anonymous guest upload with generated name and fixed code"
```

---

### Task 7: Free-account gates — 20-file cap + type gating

**Files:**
- Modify: `src/main/java/com/quickprint/file/FileService.java` (the existing `upload` method)
- Create: `src/main/java/com/quickprint/file/FileLimitReachedException.java`
- Test: `src/test/java/com/quickprint/file/FileServiceAccountLimitsTest.java`

**Interfaces:**
- Consumes: `StoredFileRepository.countByOwner(User)` (Task 4), `FileTypePolicy.allowedForFreeAccount` (Task 3), `headOf` (Task 6, same class).
- Produces: `upload(...)` unchanged signature, now throws `FileLimitReachedException` (409) at the cap and `UnsupportedFileTypeException` (415) for disallowed content.

- [ ] **Step 1: Create the 409 exception**

```java
package com.quickprint.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Free accounts hold at most a fixed number of active files at once.
@ResponseStatus(HttpStatus.CONFLICT)
public class FileLimitReachedException extends RuntimeException {

    public FileLimitReachedException(int max) {
        super("File limit reached (" + max + "). Delete a file to upload a new one.");
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.quickprint.file;

import com.quickprint.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceAccountLimitsTest {

    private static final MockMultipartFile PDF =
            new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.7 x".getBytes());

    private FileService service(StoredFileRepository repo) {
        return new FileService(repo, mock(FileStorageService.class),
                Duration.ofDays(7), Duration.ofHours(24), 20);
    }

    @Test
    void rejectsTheUploadOnceTheAccountHoldsTwentyFiles() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        when(repo.countByOwner(any())).thenReturn(20L);

        assertThatThrownBy(() -> service(repo).upload(mock(User.class), PDF, "meu-cv", null, false))
                .isInstanceOf(FileLimitReachedException.class);
    }

    @Test
    void rejectsContentTheFreeTierMayNotSend() {
        StoredFileRepository repo = mock(StoredFileRepository.class);
        when(repo.countByOwner(any())).thenReturn(0L);
        // A plain zip: only the (future) Pro tier may send archives.
        MockMultipartFile zip = new MockMultipartFile(
                "file", "backup.zip", "application/zip", new byte[]{'P', 'K', 3, 4, 0, 0});

        assertThatThrownBy(() -> service(repo).upload(mock(User.class), zip, "backup", null, false))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=FileServiceAccountLimitsTest"`
Expected: FAIL — `upload` currently accepts both calls (may fail later at the mocked repository with a different error, or simply not throw).

- [ ] **Step 4: Add the gates to upload**

In `FileService.upload(...)`, insert right after the empty-file check (before nickname validation):

```java
        FileTypePolicy.Kind kind = FileTypePolicy.detect(headOf(file));
        if (!FileTypePolicy.allowedForFreeAccount(kind)) {
            throw new UnsupportedFileTypeException();
        }
        if (files.countByOwner(owner) >= freeMaxFiles) {
            throw new FileLimitReachedException(freeMaxFiles);
        }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\mvnw.cmd test`
Expected: full suite green — BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/quickprint/file/FileService.java src/main/java/com/quickprint/file/FileLimitReachedException.java src/test/java/com/quickprint/file/FileServiceAccountLimitsTest.java
git commit -m "Gate account uploads by tier: 20-file cap and light types only"
```

---

### Task 8: GuestUploadRateLimiter

**Files:**
- Create: `src/main/java/com/quickprint/guest/GuestUploadRateLimiter.java`
- Test: `src/test/java/com/quickprint/guest/GuestUploadRateLimiterTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `guestUploadRateLimiter.tryAcquire(String key) -> boolean` — max 5 per rolling hour per key (IP). Spring `@Component`.

- [ ] **Step 1: Write the failing test**

```java
package com.quickprint.guest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestUploadRateLimiterTest {

    @Test
    void allowsFivePerHourThenBlocksTheSixth() {
        GuestUploadRateLimiter limiter = new GuestUploadRateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4")).as("attempt %d", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void limitsAreTrackedPerClient() {
        GuestUploadRateLimiter limiter = new GuestUploadRateLimiter();

        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        assertThat(limiter.tryAcquire("5.6.7.8")).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test "-Dtest=GuestUploadRateLimiterTest"`
Expected: COMPILATION ERROR — class does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.quickprint.guest;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Caps anonymous uploads per client so guest mode can't be scripted into a
// free bulk file host. Same fixed-window pattern as ReceiveRateLimiter.
// ponytail: second copy of the window logic; fold both into one shared
// FixedWindowRateLimiter if a third limiter ever appears.
@Component
public class GuestUploadRateLimiter {

    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_MS = 60 * 60_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= WINDOW_MS) {
                return new Window(now);
            }
            existing.count++;
            return existing;
        });
        return window.count <= MAX_PER_WINDOW;
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
            this.count = 1;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test "-Dtest=GuestUploadRateLimiterTest"`
Expected: `Tests run: 2, Failures: 0` — BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/quickprint/guest/GuestUploadRateLimiter.java src/test/java/com/quickprint/guest/GuestUploadRateLimiterTest.java
git commit -m "Rate-limit guest uploads per IP"
```

---

### Task 9: GuestController + SecurityConfig

**Files:**
- Create: `src/main/java/com/quickprint/guest/GuestController.java`
- Modify: `src/main/java/com/quickprint/config/SecurityConfig.java`
- Manual: curl verification (Step 3)

**Interfaces:**
- Consumes: `FileService.uploadGuest` (Task 6), `GuestUploadRateLimiter` (Task 8), `TooManyAttemptsException` (existing, `com.quickprint.receive`).
- Produces: public `POST /api/guest` (multipart field `file`) → `201 {"nickname": "...", "code": "..."}`.

- [ ] **Step 1: Write the controller**

```java
package com.quickprint.guest;

import com.quickprint.file.FileService;
import com.quickprint.receive.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

// Public, no account: anyone can send one file and gets back the generated
// nickname + fixed code to hand to the receiver. Abuse is contained by the
// per-IP rate limit, the image/PDF-only policy, one-time use and a short TTL.
@RestController
@RequestMapping("/api/guest")
public class GuestController {

    private final FileService files;
    private final GuestUploadRateLimiter rateLimiter;

    public GuestController(FileService files, GuestUploadRateLimiter rateLimiter) {
        this.files = files;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file,
                                      HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            throw new TooManyAttemptsException();
        }
        FileService.GuestUpload result = files.uploadGuest(file);
        return Map.of("nickname", result.nickname(), "code", result.code());
    }
}
```

- [ ] **Step 2: Open the endpoint in SecurityConfig**

In `SecurityConfig.java`, change the receive matcher line (~line 33) from:

```java
                        .requestMatchers("/api/receive/**").permitAll()
```

to:

```java
                        // The receiving side and guest sending need no account.
                        .requestMatchers("/api/receive/**", "/api/guest").permitAll()
```

and the CSRF exemption (~line 61) from:

```java
                        .ignoringRequestMatchers("/api/receive/**"))
```

to:

```java
                        .ignoringRequestMatchers("/api/receive/**", "/api/guest"))
```

- [ ] **Step 3: Manual end-to-end verification (app + Postgres running)**

Start: `.\mvnw.cmd spring-boot:run` (after the Task 4 SQL was applied). Then in a second PowerShell:

```powershell
# happy path: a real (tiny) PDF
Set-Content -Path guest-test.pdf -Value "%PDF-1.4 test" -Encoding ascii
curl.exe -s -i -X POST http://localhost:8080/api/guest -F "file=@guest-test.pdf;type=application/pdf"
```
Expected: `HTTP/1.1 201` and `{"nickname":"<adjective+animal+NN>","code":"<6 chars>"}`.

```powershell
# receive it once with the returned values (replace NICK and CODE):
curl.exe -s -o received.pdf -w "%{http_code}" -X POST "http://localhost:8080/api/receive/NICK" --data "code=CODE&mode=download"
# expected: 200, file saved
curl.exe -s -o NUL -w "%{http_code}" -X POST "http://localhost:8080/api/receive/NICK" --data "code=CODE&mode=download"
# expected: 404 (one-time file is gone)
```

```powershell
# type gate: an exe-looking file pretending to be a PDF
Set-Content -Path fake.pdf -Value "MZ90 not a pdf" -Encoding ascii
curl.exe -s -o NUL -w "%{http_code}" -X POST http://localhost:8080/api/guest -F "file=@fake.pdf;type=application/pdf"
# expected: 415
```

- [ ] **Step 4: Run the full suite once more**

Run: `.\mvnw.cmd test`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/quickprint/guest/GuestController.java src/main/java/com/quickprint/config/SecurityConfig.java
git commit -m "Expose public guest upload endpoint"
```

---

### Task 10: Frontend — guest mode on the landing page

**Files:**
- Modify: `src/main/resources/static/index.html`
- Create: `src/main/resources/static/js/guest.js`
- Modify: `src/main/resources/static/css/styles.css`

**Interfaces:**
- Consumes: `POST /api/guest` (Task 9): 201 `{nickname, code}`, 400/413/415/429 errors.
- Produces: UI only. Existing receive form/ids (`receive-form`, `nickname`, `code`, `receive-error`, `preview-btn`, `download-btn`) must stay untouched so `receive.js` keeps working.

- [ ] **Step 1: Add the guest section to index.html**

Inside `<main class="rp-hero">`, right AFTER the closing `</form>` of `receive-form`, add:

```html
    <button id="guest-toggle" class="rp-guest-toggle" type="button">
      Modo convidado — enviar um ficheiro sem conta
    </button>

    <section id="guest-panel" class="rp-card rp-guest" hidden>
      <p class="rp-guest-hint">Envia uma imagem ou PDF. Uso único · expira em 24h.</p>

      <input id="guest-file" type="file" accept="image/*,.pdf" hidden>
      <button id="guest-pick" class="rp-btn" type="button">Escolher ficheiro</button>
      <p id="guest-filename" class="rp-guest-filename" hidden></p>
      <button id="guest-send" class="rp-btn" type="button" hidden>Enviar</button>

      <p id="guest-error" class="rp-error" hidden></p>

      <div id="guest-result" hidden>
        <p class="rp-guest-hint">Entrega estes dois dados a quem vai receber:</p>
        <div class="rp-guest-row">
          <span class="rp-guest-label">Nome</span>
          <code id="guest-nickname" class="rp-guest-value"></code>
          <button class="rp-copy" type="button" data-copy="guest-nickname">Copiar</button>
        </div>
        <div class="rp-guest-row">
          <span class="rp-guest-label">Código</span>
          <code id="guest-code" class="rp-guest-value"></code>
          <button class="rp-copy" type="button" data-copy="guest-code">Copiar</button>
        </div>
      </div>

      <button id="guest-back" class="rp-ghost" type="button">← Voltar a receber</button>
    </section>
```

And before `<script src="js/receive.js"></script>` add:

```html
  <script src="js/guest.js"></script>
```

- [ ] **Step 2: Write js/guest.js**

```javascript
// Guest mode: send one image/PDF without an account. The server answers with a
// generated nickname + fixed code, which the sender hands to the receiver.

const guestToggle = document.getElementById("guest-toggle");
const guestPanel = document.getElementById("guest-panel");
const guestFile = document.getElementById("guest-file");
const guestPick = document.getElementById("guest-pick");
const guestFilename = document.getElementById("guest-filename");
const guestSend = document.getElementById("guest-send");
const guestError = document.getElementById("guest-error");
const guestResult = document.getElementById("guest-result");
const guestBack = document.getElementById("guest-back");
const receiveFormEl = document.getElementById("receive-form");

guestToggle.addEventListener("click", () => {
  receiveFormEl.hidden = true;
  guestToggle.hidden = true;
  guestPanel.hidden = false;
});

guestBack.addEventListener("click", () => {
  guestPanel.hidden = true;
  receiveFormEl.hidden = false;
  guestToggle.hidden = false;
});

guestPick.addEventListener("click", () => guestFile.click());

guestFile.addEventListener("change", () => {
  if (guestFile.files.length === 0) return;
  guestFilename.textContent = guestFile.files[0].name;
  guestFilename.hidden = false;
  guestSend.hidden = false;
  guestError.hidden = true;
});

guestSend.addEventListener("click", async () => {
  const body = new FormData();
  body.append("file", guestFile.files[0]);
  guestSend.disabled = true;

  let response;
  try {
    response = await fetch("/api/guest", { method: "POST", body });
  } finally {
    guestSend.disabled = false;
  }

  if (response.status === 415) {
    showGuestError("Só podes enviar imagens ou PDF em modo convidado.");
    return;
  }
  if (response.status === 413) {
    showGuestError("Ficheiro demasiado grande (máx. 25 MB).");
    return;
  }
  if (response.status === 429) {
    showGuestError("Muitos envios seguidos. Tenta novamente mais tarde.");
    return;
  }
  if (!response.ok) {
    showGuestError("Não foi possível enviar. Tenta novamente.");
    return;
  }

  const data = await response.json();
  document.getElementById("guest-nickname").textContent = data.nickname;
  document.getElementById("guest-code").textContent = data.code;
  guestPick.hidden = true;
  guestSend.hidden = true;
  guestFilename.hidden = true;
  guestResult.hidden = false;
});

// One handler for both copy buttons.
for (const btn of document.querySelectorAll(".rp-copy")) {
  btn.addEventListener("click", async () => {
    const value = document.getElementById(btn.dataset.copy).textContent;
    await navigator.clipboard.writeText(value);
    btn.textContent = "Copiado!";
    setTimeout(() => (btn.textContent = "Copiar"), 1500);
  });
}

function showGuestError(message) {
  guestError.textContent = message;
  guestError.hidden = false;
}
```

- [ ] **Step 3: Style it in styles.css**

Append inside the receive-page section of `styles.css` (after the `.rp-ghost:hover` rule):

```css
/* guest mode: toggle under the receive card, and the send panel */
.rp-guest-toggle {
  margin-top: 16px;
  background: none;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 10px 16px;
  color: var(--text-muted);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.rp-guest-toggle:hover {
  color: var(--text);
  border-color: var(--accent-ring);
}

.rp-guest {
  margin-top: 4px;
  gap: 12px;
}

.rp-guest-hint {
  font-size: 13px;
  color: var(--text-muted);
}

.rp-guest-filename {
  font-size: 13px;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-guest-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rp-guest-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  width: 52px;
}

.rp-guest-value {
  flex: 1;
  font-family: ui-monospace, "Cascadia Mono", Consolas, monospace;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.08em;
  background: var(--surface-input);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 12px;
}

.rp-copy {
  background: none;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px 12px;
  color: var(--text-muted);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.rp-copy:hover {
  color: var(--text);
  border-color: var(--accent-ring);
}

.rp-guest-toggle:focus-visible,
.rp-copy:focus-visible {
  outline: 2px solid var(--accent-ring);
  outline-offset: 2px;
}
```

- [ ] **Step 4: Verify in the browser**

Restart the app (`Ctrl+C`, `.\mvnw.cmd spring-boot:run`), open `http://localhost:8080`:
1. Click "Modo convidado" → receive form hides, guest panel shows.
2. Pick a real PDF/image → "Enviar" → result card shows a name like `sillygoose76` and a 6-char code; both "Copiar" buttons work.
3. Pick a `.txt` renamed to `.pdf` → 415 message shown.
4. "← Voltar a receber" restores the receive form.
5. Receive flow: enter the shown name + code → file downloads once; a second attempt says "Não encontrado".
6. Check both themes (☀/☾ toggle) and no console errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html src/main/resources/static/js/guest.js src/main/resources/static/css/styles.css
git commit -m "Add guest mode send flow to the landing page"
```

---

### Task 11: Docs — HANDOFF and project context

**Files:**
- Modify: `HANDOFF.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update HANDOFF.md**

In the "O que está FEITO" backend list, add:

```markdown
- Modo convidado: `POST /api/guest` (público, rate-limited 5/h/IP) — envio
  anónimo de imagem/PDF, uso único, nome gerado (`sillygoose76`) + código fixo
  de 6 caracteres, TTL 24h (`quickprint.storage.guest-ttl`)
- Limites da conta grátis: máx. 20 ficheiros ativos (`quickprint.limits.free-max-files`),
  tipos leves (imagens/PDF/Office) validados por magic bytes (`FileTypePolicy`)
```

In "O que FALTA", add:

```markdown
- Spec de pagamentos (tier Pro: vídeo/zip, quota em GB) — ver
  `docs/superpowers/specs/2026-07-16-guest-mode-design.md`, secção 11
```

In "Onde está o quê", add to the package list:

```markdown
- `guest/` — `GuestController`, `NicknameGenerator`, `GuestUploadRateLimiter`
```

- [ ] **Step 2: Update CLAUDE.md**

In "MVP Scope", after the one-time download bullet, add:

```markdown
- **Guest mode:** anyone can send ONE image/PDF without an account — the app
  generates a friendly nickname (`sillygoose76`) + a fixed 6-char code, one-time
  use, 24h TTL. Accounts are for managing multiple files (rolling codes,
  dashboard). Tier limits live in `FileTypePolicy` + config
  (`quickprint.limits.free-max-files=20`). Payments/Pro tier: separate spec.
```

- [ ] **Step 3: Commit**

```bash
git add HANDOFF.md CLAUDE.md
git commit -m "Document guest mode and tier limits"
```
