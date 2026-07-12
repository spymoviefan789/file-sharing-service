// QuickPrint dashboard — talks to the real API.
// GET /api/files lists the owner's files (with the current rolling code the
// server derives); POST /api/files uploads. Codes are server-authoritative:
// when the countdown hits zero we re-fetch to get the fresh codes.

const state = {
  files: [],
  search: "",
  type: "all",
  sortDesc: true,
  view: "list",
  selected: new Set(),
};

const collection = document.getElementById("file-collection");
const emptyState = document.getElementById("empty-state");
const searchInput = document.getElementById("search-input");
const filterType = document.getElementById("filter-type");
const sortDate = document.getElementById("sort-date");
const viewList = document.getElementById("view-list");
const viewGrid = document.getElementById("view-grid");
const selectAll = document.getElementById("select-all");
const addFile = document.getElementById("add-file");
const fileInput = document.getElementById("file-input");

// --- Auth guard -------------------------------------------------------------
// The dashboard is a static file (anyone can load it); only the API is
// protected. If there is no session, bounce to the login page. On success,
// swap the LOG IN / SIGN UP buttons for the user's email + LOG OUT.
const authActions = document.querySelector(".auth-actions");

fetch("/api/auth/me")
  .then((res) => (res.ok ? res.json() : Promise.reject()))
  .then((me) => {
    authActions.innerHTML = "";
    const email = document.createElement("span");
    email.className = "pill pill--outline";
    email.textContent = me.email;
    const logout = document.createElement("button");
    logout.type = "button";
    logout.className = "pill pill--solid";
    logout.textContent = "LOG OUT";
    logout.addEventListener("click", async () => {
      await fetch("/api/auth/logout", { method: "POST", headers: csrfHeaders() });
      window.location.href = "login.html";
    });
    authActions.append(email, logout);
    fetchFiles();
  })
  .catch(() => (window.location.href = "login.html"));

// --- Rolling code timing ----------------------------------------------------
const CODE_PERIOD_MS = 60 * 1000;
const RING_C = 2 * Math.PI * 15; // circumference of the r=15 timer ring
let codeDeadline = Date.now() + CODE_PERIOD_MS;

// --- Display helpers --------------------------------------------------------

function typeOf(contentType) {
  if (!contentType) return "doc";
  if (contentType.startsWith("image/")) return "image";
  if (contentType === "application/pdf") return "pdf";
  return "doc";
}

function formatSize(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function usesLabel(file) {
  return file.unlimited ? "∞" : `${file.usesLeft}×`;
}

// URL-safe slug suggested from the filename, for the nickname prefill.
function suggestNickname(name) {
  return name
    .replace(/\.[^.]+$/, "")
    .toLowerCase()
    .normalize("NFD").replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 20) || "ficheiro";
}

function fileIconSVG() {
  return `<svg viewBox="0 0 24 24" width="34" height="34" aria-hidden="true">
    <path fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"
      d="M6 3h8l4 4v14H6z"/><path fill="none" stroke="currentColor" stroke-width="1.6"
      stroke-linejoin="round" d="M14 3v4h4"/></svg>`;
}

function makeRing() {
  const wrap = document.createElement("span");
  wrap.className = "ring-wrap";
  wrap.innerHTML = `<svg class="ring" viewBox="0 0 36 36">
    <circle class="ring-track" cx="18" cy="18" r="15"/>
    <circle class="ring-progress" cx="18" cy="18" r="15"/></svg>`;
  return wrap;
}

// Owner clicks their own file: preview it inline (images/PDF) in a new tab, or
// download it (other types). Uses the owner endpoint — no code, no use spent.
function openFile(file) {
  const kind = typeOf(file.contentType);
  if (kind === "image" || kind === "pdf") {
    window.open(`/api/files/${file.id}/content?mode=preview`, "_blank", "noopener");
  } else {
    const link = document.createElement("a");
    link.href = `/api/files/${file.id}/content?mode=download`;
    link.download = file.originalFilename;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }
}

// A trash button that deletes the file after a confirmation.
function makeDeleteButton(file) {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "delete-btn";
  btn.title = "Remover ficheiro";
  btn.setAttribute("aria-label", `Remover ${file.originalFilename}`);
  btn.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
    <path fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
      stroke-linejoin="round" d="M4 7h16M9 7V5h6v2M7 7l1 13h8l1-13M10 11v6M14 11v6"/></svg>`;
  btn.addEventListener("click", async (event) => {
    event.stopPropagation(); // don't also trigger a preview
    if (!confirm(`Remover "${file.nickname}"? O ficheiro é apagado para sempre.`)) return;
    const res = await fetch(`/api/files/${file.id}`, { method: "DELETE", headers: csrfHeaders() });
    if (res.ok || res.status === 404) {
      state.selected.delete(file.nickname);
      await fetchFiles();
    } else if (res.status === 401) {
      window.location.href = "login.html";
    }
  });
  return btn;
}

// Makes a filename/card act as a preview trigger, keyboard included.
function asOpener(element, file) {
  element.classList.add("openable");
  element.tabIndex = 0;
  element.setAttribute("role", "button");
  element.title = "Clica para ver";
  element.addEventListener("click", () => openFile(file));
  element.addEventListener("keydown", (e) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      openFile(file);
    }
  });
}

function visibleFiles() {
  return state.files
    .filter((f) => f.originalFilename.toLowerCase().includes(state.search) || f.nickname.includes(state.search))
    .filter((f) => state.type === "all" || typeOf(f.contentType) === state.type)
    .sort((a, b) =>
      state.sortDesc ? b.createdAt.localeCompare(a.createdAt) : a.createdAt.localeCompare(b.createdAt)
    );
}

function makeSelectCircle(file) {
  const circle = document.createElement("button");
  circle.type = "button";
  circle.className = "select-circle";
  circle.setAttribute("aria-label", `Selecionar ${file.originalFilename}`);
  if (state.selected.has(file.nickname)) circle.classList.add("is-selected");
  circle.setAttribute("aria-pressed", String(state.selected.has(file.nickname)));
  circle.addEventListener("click", () => {
    state.selected.has(file.nickname) ? state.selected.delete(file.nickname) : state.selected.add(file.nickname);
    render();
  });
  return circle;
}

function render() {
  const files = visibleFiles();
  collection.className = state.view === "grid" ? "files files--grid" : "files files--list";
  collection.innerHTML = "";

  for (const file of files) {
    const item = document.createElement("li");
    item.className = "file";

    const code = document.createElement("span");
    code.className = "row-code";
    code.textContent = file.code;

    const ring = makeRing();

    if (state.view === "grid") {
      const icon = document.createElement("span");
      icon.className = "card-icon";
      icon.innerHTML = fileIconSVG();

      const nick = document.createElement("span");
      nick.className = "card-nick";
      nick.textContent = file.nickname;

      const codeRow = document.createElement("span");
      codeRow.className = "card-code-row";
      codeRow.append(code, ring);

      asOpener(icon, file);
      asOpener(nick, file);
      item.append(makeSelectCircle(file), icon, nick, codeRow, makeDeleteButton(file));
    } else {
      const name = document.createElement("span");
      name.className = "file-name";
      name.textContent = file.originalFilename;
      const nick = document.createElement("span");
      nick.className = "file-nick";
      nick.textContent = `/${file.nickname}`;
      name.appendChild(nick);
      asOpener(name, file);

      const meta = document.createElement("span");
      meta.className = "file-meta";
      const date = document.createElement("span");
      date.className = "row-date";
      date.textContent = file.createdAt.slice(0, 10);
      const size = document.createElement("span");
      size.className = "row-size";
      size.textContent = formatSize(file.sizeBytes);

      const uses = document.createElement("span");
      uses.className = "row-uses";
      uses.textContent = usesLabel(file);
      uses.title = file.unlimited ? "Utilizações ilimitadas" : `${file.usesLeft} utilizações restantes`;

      meta.append(date, size, uses, code, ring, makeDeleteButton(file));
      item.append(makeSelectCircle(file), name, meta);
    }

    item._ringEl = ring.querySelector(".ring-progress");
    collection.appendChild(item);
  }

  emptyState.hidden = files.length > 0;
  const allSelected = files.length > 0 && files.every((f) => state.selected.has(f.nickname));
  selectAll.classList.toggle("is-selected", allSelected);
  selectAll.setAttribute("aria-pressed", String(allSelected));
  updateTimers();
}

// Drives every ring from the shared deadline. When it runs out the codes have
// rolled server-side, so we re-fetch to get the new ones.
function updateTimers() {
  if (!state.files.length) return;
  const remaining = Math.max(0, codeDeadline - Date.now());
  if (remaining === 0) {
    fetchFiles();
    return;
  }
  const offset = (RING_C * (1 - remaining / CODE_PERIOD_MS)).toFixed(2);
  for (const li of collection.children) {
    if (li._ringEl) li._ringEl.style.strokeDashoffset = offset;
  }
}
setInterval(updateTimers, 250);

async function fetchFiles() {
  const res = await fetch("/api/files");
  if (!res.ok) {
    if (res.status === 401) window.location.href = "login.html";
    return;
  }
  state.files = await res.json();
  codeDeadline = Date.now() + (state.files.length ? state.files[0].codeExpiresInSeconds * 1000 : CODE_PERIOD_MS);
  render();
}

searchInput.addEventListener("input", () => {
  state.search = searchInput.value.trim().toLowerCase();
  render();
});

filterType.addEventListener("change", () => {
  state.type = filterType.value;
  render();
});

sortDate.addEventListener("click", () => {
  state.sortDesc = !state.sortDesc;
  sortDate.textContent = state.sortDesc ? "DATE ↓" : "DATE ↑";
  sortDate.setAttribute("aria-pressed", String(!state.sortDesc));
  render();
});

viewList.addEventListener("click", () => {
  state.view = "list";
  viewList.classList.add("is-active");
  viewGrid.classList.remove("is-active");
  render();
});

viewGrid.addEventListener("click", () => {
  state.view = "grid";
  viewGrid.classList.add("is-active");
  viewList.classList.remove("is-active");
  render();
});

selectAll.addEventListener("click", () => {
  const files = visibleFiles();
  const allSelected = files.length > 0 && files.every((f) => state.selected.has(f.nickname));
  files.forEach((f) => (allSelected ? state.selected.delete(f.nickname) : state.selected.add(f.nickname)));
  render();
});

// --- Upload flow: per file, choose a nickname + usage limit -----------------

const uploadDialog = document.getElementById("upload-dialog");
const uploadFileName = document.getElementById("upload-file-name");
const uploadNickname = document.getElementById("upload-nickname");
const uploadError = document.getElementById("upload-error");
const uploadCancel = document.getElementById("upload-cancel");
const limitButtons = document.querySelectorAll(".limit-btn");

let uploadQueue = []; // picked files waiting to be configured

addFile.addEventListener("click", () => fileInput.click());

fileInput.addEventListener("change", () => {
  uploadQueue = [...fileInput.files];
  fileInput.value = "";
  promptNextUpload();
});

function promptNextUpload() {
  if (uploadQueue.length === 0) {
    if (uploadDialog.open) uploadDialog.close();
    return;
  }
  const f = uploadQueue[0];
  uploadFileName.textContent = f.name;
  uploadNickname.value = suggestNickname(f.name);
  uploadError.hidden = true;
  if (!uploadDialog.open) uploadDialog.showModal();
}

// A limit button picks the allowance and uploads. On success the file moves on;
// on a validation/conflict error the dialog stays so the user can fix it.
for (const btn of limitButtons) {
  btn.addEventListener("click", async () => {
    const nickname = uploadNickname.value.trim().toLowerCase();
    if (!/^[a-z0-9-]{1,20}$/.test(nickname)) {
      showUploadError("O nickname deve ter 1–20 caracteres: letras, números ou hífens.");
      return;
    }
    const limit = btn.dataset.limit === "inf" ? null : parseInt(btn.dataset.limit, 10);

    const body = new FormData();
    body.append("file", uploadQueue[0]);
    body.append("nickname", nickname);
    if (limit !== null) body.append("shareLimit", String(limit));

    const res = await fetch("/api/files", { method: "POST", headers: csrfHeaders(), body });
    if (res.ok) {
      uploadQueue.shift();
      await fetchFiles();
      promptNextUpload();
    } else if (res.status === 409) {
      showUploadError("Esse nickname já está em uso. Escolhe outro.");
    } else if (res.status === 401) {
      window.location.href = "login.html";
    } else {
      showUploadError("Não foi possível carregar o ficheiro. Verifica o nickname e tenta de novo.");
    }
  });
}

function showUploadError(message) {
  uploadError.textContent = message;
  uploadError.hidden = false;
}

// Cancelar skips the current file (not uploaded).
uploadCancel.addEventListener("click", () => {
  uploadQueue.shift();
  promptNextUpload();
});
