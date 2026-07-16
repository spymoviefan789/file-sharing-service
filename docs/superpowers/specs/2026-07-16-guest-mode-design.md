# Design — Modo convidado (envio anónimo) + estrutura de tiers

**Data:** 2026-07-16
**Estado:** aprovado (design), por implementar
**Contexto do produto:** ver `CLAUDE.md` e `HANDOFF.md`

## 1. Objetivo

Permitir **enviar um ficheiro sem conta** ("modo convidado"), mantendo o modelo
de conta atual intacto. Introduzir uma **estrutura de tiers/limites** centralizada
que deixe a app *pronta para monetizar*, **sem** construir pagamentos (esse é um
spec próprio, separado).

### Fora de âmbito (spec futuro)
- Integração de pagamentos (subscrição ou pagamento único, Stripe, planos,
  faturas, webhooks).
- Coluna `User.tier` e compra do tier Pro — o Pro fica documentado como
  *placeholder*, não comprável.
- Caso mobile do formulário de receção no dashboard.

## 2. Modelo de dados (`StoredFile`)

- `owner` passa a **nullable** (`owner_id` nulo ⇒ ficheiro de convidado).
- Novo campo **`fixedCode`** (`VARCHAR`, nullable): preenchido nos convidados;
  **nulo** nas contas (que continuam a usar `secret` + código rolante).
- Convidado: `oneTime = true`, `shareLimit = 1`, `usesLeft = 1` (uso único — já
  suportado; apaga-se após a 1ª receção via `isOneTime()`).
- `nickname` continua **único entre todos os ficheiros** (convidado + conta
  partilham o namespace). É o que evita a ambiguidade de uma tabela separada.

### Migração (importante)
`spring.jpa.hibernate.ddl-auto=update` **não relaxa** um `NOT NULL` existente.
A coluna `owner_id` já existe como `NOT NULL`. É preciso um passo manual:

```sql
ALTER TABLE stored_files ALTER COLUMN owner_id DROP NOT NULL;
```

(ou introduzir Flyway — recomendado a prazo, ver HANDOFF). O `fixedCode` novo é
adicionado automaticamente pelo Hibernate (coluna nullable nova).

## 3. Geração no envio convidado

- **Nome amigável:** `adjetivo + animal + 2 dígitos` (ex: `sillygoose76`),
  minúsculas, dentro do slug atual `[a-z0-9-]{1,20}`. Duas pequenas listas de
  palavras. Repete a geração se o nickname já existir (poucas colisões dado o
  espaço adjetivos × animais × 100).
- **Código fixo:** 6 caracteres maiúsculos, **alfabeto sem ambíguos**
  (sem `O/0`, `I/1`), para casar com o formato do campo de receção (`A1B2C3`).
  Guardado em `fixedCode`. Espaço ≈ (32)^6.

## 4. Envio convidado (backend)

- Novo endpoint público **`POST /api/guest`** (multipart, só o ficheiro).
- **Sem sessão**; **rate-limit por IP**; **isento de CSRF** (stateless, como
  `/api/receive/**`). Ambos configurados no `SecurityConfig`.
- Aplica os limites do tier Convidado (secção 6), gera nome + código, guarda o
  `StoredFile` (owner nulo, fixedCode, oneTime, TTL curto), e devolve
  **`201 { "nickname": "...", "code": "..." }`** — o código é mostrado uma vez.
- Reutiliza `FileStorageService` (bytes em disco com nome UUID) e o gerador de
  segredo não é preciso para convidados (usam `fixedCode`).

### Componentes novos/alterados
- `GuestController` (`POST /api/guest`).
- `FileService.uploadGuest(...)` — variante sem owner, com nome/código gerados.
- `NicknameGenerator` (nome amigável) e `FixedCodes` (gerar/validar código fixo).
- `GuestUploadRateLimiter` (padrão do `ReceiveRateLimiter` existente).
- `FileTypePolicy` — deteta o tipo real por **magic bytes** e verifica se é
  permitido para o tier em uso.
- `FileService.upload(...)` (conta) — passa a impor o **tecto de ficheiros**
  (conta os ativos do dono; erro se exceder) e o gating de tipo do tier.

## 5. Receção (mudança mínima)

No `ReceiveService.fetch`:
- Se `file.getFixedCode() != null` → **comparação em tempo-constante**
  (`MessageDigest.isEqual`) com o código submetido (após `trim/upper`).
- Senão → validação rolante `AccessCodes.isValid(secret, code)` (como hoje).
- Restante inalterado: filtro de expiração, decremento atómico, apagar no uso
  único, **404 genérico** para todos os falhos.

## 6. Tiers / limites (pronto para monetizar)

Limites centralizados **num só sítio** (ex: `Tier` enum + política de limites),
em vez de números espalhados pelo código.

| Tier | Tipos permitidos | Tamanho máx | Nº ficheiros ativos | TTL | Código | Gestão |
|---|---|---|---|---|---|---|
| **Convidado** | imagem **ou** PDF | 25 MB | — (limite por IP) | 24h | fixo, uso único | nenhuma |
| **Conta grátis** | imagens, PDF, docs leves | 25 MB | **20** | 7 dias | rolante, ∞/1/5/10 | dashboard |
| **Pro** *(placeholder, pago)* | **+ vídeo, zip**, grandes | maior (a definir) | **quota em GB** (ex: 6/11/23) | longo (a definir) | rolante | dashboard |

- **Gating por tipo:** o tier define os tipos permitidos. Convidado = imagem/PDF;
  conta grátis = tipos leves; vídeo/zip só no Pro.
- **Validação por conteúdo:** o tipo é validado pelos **magic bytes** do ficheiro,
  não pelo `content-type` do browser (falsificável). Para os tipos do convidado
  (imagem/PDF) chega uma verificação pequena de assinaturas — **sem** adicionar
  dependência (ex: Tika) agora (YAGNI).
- **Tecto de ficheiros da conta grátis:** máx. **20** ficheiros **ativos** por
  conta, aplicado no upload (conta os do dono; erro claro se exceder). Limite novo.
  *(Alternativa registada: exprimir a conta grátis como quota pequena em GB
  em vez de nº de ficheiros, para um sistema de uma só métrica — não adotado agora.)*
- Os limites de tipo/tamanho/TTL/tecto passam todos por esta política.
- **Sem** coluna `User.tier` agora (YAGNI). Ponto de extensão documentado: quando
  o pagamento existir, adiciona-se `User.tier` (default FREE) e a política passa a
  resolver por tier do utilizador.
- `quickprint.storage.guest-ttl` (novo, ex: `PT24H`) além do
  `quickprint.storage.default-ttl` (`P7D`) já existente.
- Config nova: tecto de ficheiros da conta grátis (ex:
  `quickprint.limits.free-max-files=20`).

## 7. Frontend (modo convidado)

- `index.html` (landing/receção): botão de destaque **"Modo convidado"**.
- Ao clicar, revela o fluxo de **envio** na mesma página: escolher ficheiro →
  *Enviar* → **cartão de resultado** com **nome + código** (botões de copiar) e
  aviso "uso único · expira em 24h". Voltar mostra de novo a receção.
- Receção continua a ser o modo por defeito. JS novo `guest.js` faz o
  `POST /api/guest` e apresenta o resultado. Estilo reutiliza os tokens/`rp-*`.

## 8. Segurança / abuso

- **Rate-limit** no envio convidado (ex: 5 envios/hora/IP; em memória, como o
  `ReceiveRateLimiter`).
- Tamanho máx aplicado (multipart já a 25 MB) + **TTL curto** + **uso único**
  reduzem a janela de exposição e o abuso de disco.
- Nome gerado + código fixo + **404 genérico** + rate-limit de receção
  dificultam adivinhação/enumeração.
- **Tipo como fronteira de segurança:** validado por **magic bytes**, não pelo
  `content-type` do browser — impede renomear um `.exe` para `.pdf` no convidado.
- Preview continua **restrito a imagens/PDF** (nunca servir HTML inline).
- **Trade-off consciente:** o código fixo é mais adivinhável que o rolante.
  Mitigado pelo conjunto acima; aceitável neste contexto. Registado aqui.

## 9. Erros (contratos)

- `POST /api/guest`: ficheiro vazio → 400; **tipo não permitido** (não é
  imagem/PDF por magic bytes) → 415; demasiado grande → 413; rate-limit → 429;
  sucesso → 201 `{ nickname, code }`.
- `POST /api/files` (conta): **tecto de ficheiros excedido** → 409 com mensagem
  clara; tipo não permitido para o tier → 415.
- Receção de ficheiro de convidado: mesma semântica de **404 genérico**.

## 10. Testes

- `NicknameGeneratorTest` — formato do slug e unicidade sob colisão.
- `FixedCodesTest` — geração (alfabeto/comprimento) e validação em tempo-constante.
- Caminho do código fixo no `ReceiveService` (código certo/errado, uso único
  apaga).
- Resolução de limites por tier (Convidado vs Conta).
- `FileTypePolicyTest` — magic bytes: aceita PNG/JPEG/PDF, rejeita um `.exe`
  renomeado para `.pdf`.
- Tecto de ficheiros da conta grátis (permite até ao limite, 409 ao exceder).
- Mantêm-se `AccessCodesTest`, `ReceiveRateLimiterTest`.

## 11. Ponto de extensão para monetização (futuro)

Quando o spec de pagamentos existir:
1. `User.tier` (enum, default FREE).
2. A política de limites resolve por `user.tier` (Pro desbloqueia **tipos**
   vídeo/zip, tamanho e TTL maiores).
3. **Eixo de monetização escolhido:** vender por **capacidade em GB** (quota de
   armazenamento por conta), com uma escada de planos (ex: 6/11/23 GB — valores
   exatos a fechar no spec de pagamentos). A política de limites deve exprimir
   uma quota de bytes por conta, não só nº de ficheiros.
4. Fluxo de checkout (fornecedor externo) atualiza o tier — camada separada.
