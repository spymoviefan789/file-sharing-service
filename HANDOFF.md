# QuickPrint — Handoff

> Ponto de situação para retomar o projeto numa sessão nova.
> Contexto completo do produto e decisões de design: ver `CLAUDE.md`.

## Estado geral

MVP **funcional de ponta a ponta**, testado à mão (curl + browser) contra
PostgreSQL real. Tudo commitado, árvore de trabalho limpa.

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

## ⚠️ Começar por aqui — ponto em aberto

Report do Tomás (por confirmar, pode ser bug ou mal-entendido):
> "No preview de um ficheiro com acesso único, não decrementa e não aparece
> botão para descarregar."

Duas hipóteses a distinguir primeiro:
1. **Preview do lado de quem recebe** (`index.html` → `POST /api/receive/{nickname}`
   com `mode=preview`): o backend **decrementa** uma utilização (`ReceiveService.fetch`).
   Se na prática não estiver a decrementar, é **bug real** — investigar.
2. **Preview do dono** (clicar no ficheiro no dashboard → `GET /api/files/{id}/content`):
   **não decrementa de propósito** (o dono vê o próprio ficheiro de graça) e
   **não há botão de download explícito** no dashboard. Provavelmente foi isto
   que o Tomás viu — comportamento esperado, mas falta um botão de download.

Ação: confirmar o caso e, ou corrigir o decremento (se for na receção), ou
adicionar um botão de download explícito por ficheiro no dashboard.

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
- Testes unitários (6, passam): `AccessCodesTest`, `ReceiveRateLimiterTest`

### Frontend (HTML/CSS/JS em `src/main/resources/static/`)
- `index.html` — página de receção (landing aberta), nome + código, VER/DESCARREGAR
- `login.html`, `signup.html` — ligados à API real
- `dashboard.html` — lista/grelha, upload (nickname + botões ∞/1/5/10), colunas
  alinhadas, códigos do servidor com anel de contagem, clicar no ficheiro
  pré-visualiza, botão de apagar com diálogo de confirmação **do site** (não o
  popup nativo), link RECEBER FICHEIRO
- Navegação fechada nos dois sentidos (painel ↔ receção)

## O que FALTA
- Resolver o ponto em aberto acima
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
- `src/main/resources/static/` — frontend (html, css/styles.css, js/*)
- `src/test/java/...` — `AccessCodesTest`, `ReceiveRateLimiterTest`

## Convenções (ver CLAUDE.md para o detalhe)
- Mentor mode: explicar o porquê antes do quê; passos pequenos; esperar confirmação.
- Sem Lombok; código explícito.
- Responder na língua do Tomás (PT/EN).
- Ponytail ativo: solução mínima que funciona, stdlib primeiro.

## Conta de teste (na BD de dev)
- email `tomas@exemplo.pt`, password `umapasswordboa`
