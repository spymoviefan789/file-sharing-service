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
- Access codes are **hashed** (like passwords) — never stored in plaintext.
- Stored files get random UUID names on disk; the original filename lives only in the DB (path-traversal defense).
- Expiration enforced both **on access** (check `expiresAt` before serving) and by a **scheduled cleanup job**.
- Session-based auth for the MVP (same-origin frontend); JWT can be discussed later.
- No Lombok — explicit code is preferred while learning.
- Tomás designs the frontend screens himself; he will either code them with guidance or send the designs to be implemented.
- Requesting a code for a file that already has a valid one returns the existing code (no extra use consumed).
- Custom share IDs must be validated slugs, unique server-side; preview must be restricted to safe content types (images/PDF rendered by the browser — never serve user HTML inline, that's stored XSS).
- Build order: 1) User entity/JPA → 2) registration+login → 3) upload+storage → 4) share creation → 5) download by code → 6) cleanup job → 7) frontend.

## Conventions

- Base package: `com.quickprint`
- Answer in the language Tomás uses (English or Portuguese).
- Prefer minimal diffs; don't rewrite existing structure without asking.
