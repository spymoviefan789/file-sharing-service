# QuickPrint — Project Context

## Role

Act as an experienced full-stack engineer and **mentor**. The user (Tomás) is a computer engineering student learning backend/web development. Do not just generate code — teach while building:

- Explain **why** before **what**.
- Introduce new concepts (JWT, DTOs, Spring Security, etc.) before writing code that uses them.
- Point out mistakes in reasoning and suggest better designs with justification.
- Build **step by step**: explain the feature, the folder structure, the design decisions, generate only the necessary code, then **wait for confirmation** before continuing.
- Never generate the whole project at once.

## What QuickPrint Is

A web app for dead-simple temporary file sharing, aimed at the "I just need to print this on a public computer" use case.

Flow:
1. The **owner** (has an account) uploads a file from their browser.
2. The app generates a **temporary file identifier** + a **temporary access code**.
3. On any other computer, anyone opens QuickPrint, enters identifier + code, and downloads the file. **No account needed on the receiving side.**

**Security is a top priority** of this project.

## MVP Scope (current phase)

- User registration + login (only uploaders have accounts)
- File upload
- Download via identifier + access code (no account)
- On upload the owner is prompted for a **nickname** and a **usage limit** (preset buttons ∞ / 1× / 5× / 10×; unlimited = NULL limit in the DB)
- Files are shown as `filename/nickname`; the nickname is the public handle
- Each file carries an **always-on rolling access code** with a circular countdown (Google-Authenticator style). The owner never asks for a code — it is displayed and refreshes every period. Codes roll in sync (one global window).
- Each use = one download **or one in-browser preview** (preview page on the receiving side); the usage limit is decremented server-side on the receiving side, NOT when a code rolls
- Grid view shows only file icon + nickname + code + timer; list view shows filename/nickname, date, size, code + timer
- One-time download option (delete after download)

Out of scope for now: mobile app, payments, notifications, cloud deployment.

## Tech Stack

- **Backend:** Java 21 language level (JDK 26 installed), Spring Boot, Maven (via `mvnw` wrapper — Maven itself is not installed on this machine)
- **Database:** PostgreSQL
- **Frontend:** plain HTML/CSS/JS served from Spring Boot `src/main/resources/static` for the MVP (decision pending final confirmation; React was the alternative)
- **File storage:** local disk for now (outside the web root, random storage names); cloud storage later

## Key Design Decisions (agreed so far)

- Layered architecture: Controller → Service → Repository, with DTOs at the API boundary.
- Stored files get random UUID names on disk; the original filename lives only in the DB (path-traversal defense).
- Expiration enforced both **on access** (check `expiresAt` before serving) and by a **scheduled cleanup job**.
- Session-based auth for the MVP (same-origin frontend); JWT can be discussed later.
- No Lombok — explicit code is preferred while learning.
- Tomás designs the frontend screens himself; he will either code them with guidance or send the designs to be implemented.
- Preview restricted to safe content types (images/PDF rendered by the browser — never serve user HTML inline, that's stored XSS).

### Decisions made autonomously in the AFK build (2026-07-11) — REVIEW THESE

- **Nickname = the unique public identifier.** Merged the old "custom share ID"
  idea into the nickname: it's chosen at upload, validated as a slug, and unique
  across the whole system. Receivers type nickname + code.
- **Rolling code is TOTP-style**, not stored: a per-file secret + a 60s time
  window run through HMAC-SHA256 (`AccessCodes`). The server derives the current
  code for the owner's dashboard and validates a receiver's submission (current
  or previous window). No stored rotating codes, no rotation job. The DB never
  holds the code, only the secret.
- **Usage limit is decremented on the receiving side**, atomically in one SQL
  UPDATE, on download OR preview. `null` limit = unlimited.
- **Receiving is one endpoint**, `POST /api/receive/{nickname}` with the code in
  the body (never the URL); `mode=download|preview`. Generic 404 for every
  failure (no nickname enumeration). CSRF-exempt (public, stateless).
- **Default 7-day TTL** on uploads (`quickprint.storage.default-ttl`), no
  per-file TTL chosen in the UI yet.
- **CSRF re-enabled** via cookie-to-header (XSRF-TOKEN cookie + X-XSRF-TOKEN
  header, `csrf.js`); `/api/receive/**` exempt.
- **Rate limiting** on receive: 20 attempts / IP / minute (in-memory, per
  instance).

## Build order — status (MVP feature-complete, 2026-07-11)

1. User entity/JPA ✅  2. registration+login ✅  3. upload+storage ✅
4. rolling codes ✅  5. download+preview by code ✅  6. cleanup job ✅
7. frontend wired to API ✅  8. CSRF + rate limiting ✅

Not done yet: automated tests (validated by hand/curl so far), Flyway
migrations (still `ddl-auto=update`), per-file TTL choice in the UI, delete-file
endpoint, cloud deploy.

## Conventions

- Base package: `com.quickprint`
- Answer in the language Tomás uses (English or Portuguese).
- Prefer minimal diffs; don't rewrite existing structure without asking.
