# QuickPrint — Handoff

> Ponto de situação para retomar o projeto numa sessão nova.
> Contexto completo do produto e decisões de design: ver `CLAUDE.md`.

## Estado geral

MVP **funcional de ponta a ponta** + **modo convidado completo** (envio anónimo
de imagem/PDF sem conta). Testado: 225 testes unitários verdes + verificação
manual (curl + browser). Tudo no `master` (o branch `guest-mode` foi merged e
apagado), árvore de trabalho limpa. Sem remote configurado (repo só local).

A app **não fica a correr entre sessões** — arranca com:

```
.\mvnw.cmd spring-boot:run
```

Requisitos para arrancar:
- PostgreSQL a correr localmente, base de dados `quickprint` criada.
- Password do utilizador `postgres` (default `postgres`, ou define `DB_PASSWORD`).
- Serve tudo em `http://localhost:8080` (frontend estático + API).

Nota: `spring-boot:run` copia os ficheiros estáticos no arranque — depois de
editar `src/main/resources/static/**` é preciso **reiniciar** para os apanhar.

## Resolvido (2026-07-16)

- O "logout ao carregar em RECEBER FICHEIRO" era perceção, não bug: a sessão
  nunca terminava — a página de receção apenas mostrava Log in/Register. O
  botão redundante foi removido; a receção agora vive na barra esquerda do
  dashboard (mesmo `receive.js`, ids partilhados).
- Frontend redesenhado (estilo bypass.city): tema claro/escuro com toggle
  (`js/theme.js`, tokens em `styles.css`), fonte Inter, acento laranja→rosa.
- Bug do painel de convidado sempre visível: `.rp-card { display: flex }`
  ganhava ao atributo `hidden` (regra fraca do user-agent). Corrigido com
  `[hidden] { display: none !important; }` global no topo do `styles.css`.
  Lição: verificar visibilidade por `getComputedStyle(...).display`, não pela
  propriedade `.hidden`.

## O que está FEITO (e testado)

### Backend (Spring Boot 4.1, Java 21, PostgreSQL)
- Registo: `POST /api/auth/register` — BCrypt, email único, validação (201/400/409)
- Login/logout por sessão: `POST /api/auth/login`, `/logout`, `GET /api/auth/me`
  (cookie de sessão; 401 quando não autenticado, não redirect)
- Upload + storage: `POST /api/files` (multipart) — bytes em disco com nome UUID
  fora da raiz web; metadados na BD; nickname único (slug)
- Listar: `GET /api/files` — só do dono, devolve o código atual + segundos restantes
- Código rolante estilo Google Authenticator: HMAC-SHA256 sobre janela de 60s
  (`AccessCodes`), derivado no servidor, **nunca guardado** (só o segredo)
- Receção pública: `POST /api/receive/{nickname}` — código no corpo (nunca no URL),
  consome 1 utilização (decremento atómico em SQL), `mode=download|preview`
  (preview só imagens/PDF), 404 genérico para todos os falhos, one-time apaga
  após servir
- Preview do dono: `GET /api/files/{id}/content?mode=preview|download` — sem código,
  sem gastar uso, só do dono (404 se não for dele)
- Apagar: `DELETE /api/files/{id}` — só do dono, apaga disco + BD (204)
- Job de limpeza agendado (`CleanupJob`) dos ficheiros expirados
- CSRF reativado (cookie XSRF-TOKEN → header X-XSRF-TOKEN); `/api/receive/**` isento
- Rate limiting na receção: 20 tentativas / IP / minuto (em memória)
- Modo convidado: `POST /api/guest` (público, rate-limited 5/h/IP) — envio
  anónimo de imagem/PDF, uso único, nome gerado (`sillygoose76`) + código fixo
  de 6 caracteres, TTL 24h (`quickprint.storage.guest-ttl`)
- Limites da conta grátis: máx. 20 ficheiros ativos (`quickprint.limits.free-max-files`),
  tipos leves (imagens/PDF/Office) validados por magic bytes (`FileTypePolicy`)
- Migração manual aplicada: `ALTER TABLE stored_files ALTER COLUMN owner_id DROP NOT NULL`
  (ddl-auto=update não relaxa NOT NULL)
- Testes unitários (passam): `AccessCodesTest`, `ReceiveRateLimiterTest`,
  `FixedCodesTest`, `NicknameGeneratorTest`, `FileTypePolicyTest`,
  `ReceiveServiceGuestTest`, `FileServiceGuestUploadTest`,
  `FileServiceAccountLimitsTest`, `GuestUploadRateLimiterTest`

### Frontend (HTML/CSS/JS em `src/main/resources/static/`)
- `index.html` — página de receção (landing aberta), nome + código, VER/DESCARREGAR
- `login.html`, `signup.html` — ligados à API real
- `dashboard.html` — lista/grelha, upload (nickname + botões ∞/1/5/10), colunas
  alinhadas, códigos do servidor com anel de contagem, clicar no ficheiro
  pré-visualiza, botão de apagar com diálogo de confirmação **do site** (não o
  popup nativo), link RECEBER FICHEIRO
- Navegação fechada nos dois sentidos (painel ↔ receção)

## O que FALTA
- Spec de pagamentos (tier Pro: vídeo/zip, quota em GB) — ver
  `docs/superpowers/specs/2026-07-16-guest-mode-design.md`, secção 11
- Apagar ficheiro de conta quando chega a 0× utilizações (hoje fica no
  dashboard a "0×"; só os one-time se apagam sozinhos)
- Escolher validade (TTL) no UI (atualmente fixo 7 dias, `quickprint.storage.default-ttl`)
- Apagar vários selecionados (os círculos de seleção ainda não fazem nada)
- Migrações Flyway (ainda `spring.jpa.hibernate.ddl-auto=update`)
- Mais testes (só 2 unitários; falta integração de auth/upload/receive)
- Deploy/cloud (fora de scope do MVP)

## Onde está o quê (base package `com.quickprint`)
- `user/` — `User`, `UserRepository`, `UserService`, `EmailAlreadyUsedException`
- `auth/` — `AuthController`, `dto/RegisterRequest`
- `security/` — `AppUserDetailsService`, `CurrentUser`, `CsrfCookieFilter`
- `config/` — `SecurityConfig`
- `file/` — `StoredFile`, `StoredFileRepository`, `FileService`, `FileController`,
  `FileStorageService`, `AccessCodes`, `ContentTypes`, `CleanupJob`, `FileResponse`,
  exceções
- `receive/` — `ReceiveController`, `ReceiveService`, `ReceiveRateLimiter`, exceções
- `guest/` — `GuestController`, `NicknameGenerator`, `GuestUploadRateLimiter`
- `src/main/resources/static/` — frontend (html, css/styles.css, js/theme.js,
  js/guest.js, js/receive.js, js/dashboard.js, …)
- `src/test/java/...` — 9 classes de teste (225 testes; lista na secção FEITO)
- `docs/superpowers/specs|plans/` — spec e plano do modo convidado (2026-07-16)
- `.claude/launch.json` — preview estático do frontend sem Spring
  (`jwebserver`, porta 8001; o dashboard não funciona aí, precisa da API)

## Convenções (ver CLAUDE.md para o detalhe)
- Mentor mode: explicar o porquê antes do quê; passos pequenos; esperar confirmação.
- Sem Lombok; código explícito.
- Responder na língua do Tomás (PT/EN).
- Ponytail ativo: solução mínima que funciona, stdlib primeiro.

## Conta de teste (na BD de dev)
- A BD foi recriada em 2026-07-16 (tabelas novas via ddl-auto) — a conta antiga
  `tomas@exemplo.pt` já não existe. O Tomás criou uma conta nova no browser
  (credenciais só ele sabe); para testar do zero, registar outra em /signup.
- Migração manual já aplicada nesta BD:
  `ALTER TABLE stored_files ALTER COLUMN owner_id DROP NOT NULL;`
  (Numa BD nova esta migração NÃO é precisa — o Hibernate já cria `owner_id`
  como nullable a partir do mapeamento atual.)
