// QuickPrint dashboard — front-end prototype (mock data; "TODO(api)" marks
// where a real backend call goes later).

// nickname = public handle; shareLimit/sharesLeft = usage allowance
// (Infinity = unlimited). code rolls globally, Google-Authenticator style.
// TODO(api): replace with GET /api/files
const mockFiles = [
  { name: "Relatorio_Circuitos.pdf", type: "pdf", size: "1.2 MB", date: "2026-07-08", nickname: "relatorio", shareLimit: 3, sharesLeft: 3 },
  { name: "apontamentos_sinais.docx", type: "doc", size: "340 KB", date: "2026-07-05", nickname: "sinais", shareLimit: Infinity, sharesLeft: Infinity },
  { name: "horario_2026.png", type: "image", size: "88 KB", date: "2026-06-30", nickname: "horario", shareLimit: 5, sharesLeft: 5 },
  { name: "guia_laboratorio.pdf", type: "pdf", size: "2.8 MB", date: "2026-06-21", nickname: "guia", shareLimit: 1, sharesLeft: 0 },
];

const state = {
  files: [...mockFiles],
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

// --- Rolling access code ----------------------------------------------------
// One global window: every file's code refreshes together, like Google
// Authenticator. The visible timer is UX only — the backend enforces the real
// expiry (never trust a client clock).
const CODE_PERIOD_MS = 60 * 1000;
const RING_C = 2 * Math.PI * 15; // circumference of the r=15 timer ring
let windowEnd = Date.now() + CODE_PERIOD_MS;

// Unambiguous alphabet: no 0/O or 1/I/L that people misread when typing a code.
const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

function randomCode(length) {
  // TODO(api): real code is server-generated and stored only as a hash.
  let out = "";
  const buf = new Uint32Array(length);
  crypto.getRandomValues(buf);
  for (const n of buf) out += CODE_ALPHABET[n % CODE_ALPHABET.length];
  return out;
}

for (const f of state.files) f.code = randomCode(6);

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

function usesLabel(file) {
  return Number.isFinite(file.shareLimit) ? `${file.sharesLeft}×` : "∞";
}

function visibleFiles() {
  return state.files
    .filter((f) => f.name.toLowerCase().includes(state.search) || f.nickname.includes(state.search))
    .filter((f) => state.type === "all" || f.type === state.type)
    .sort((a, b) =>
      state.sortDesc ? b.date.localeCompare(a.date) : a.date.localeCompare(b.date)
    );
}

function makeSelectCircle(file) {
  const circle = document.createElement("button");
  circle.type = "button";
  circle.className = "select-circle";
  circle.setAttribute("aria-label", `Selecionar ${file.name}`);
  if (state.selected.has(file.name)) circle.classList.add("is-selected");
  circle.setAttribute("aria-pressed", String(state.selected.has(file.name)));
  circle.addEventListener("click", () => {
    state.selected.has(file.name) ? state.selected.delete(file.name) : state.selected.add(file.name);
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
      nick.title = file.name;

      const codeRow = document.createElement("span");
      codeRow.className = "card-code-row";
      codeRow.append(code, ring);

      item.append(makeSelectCircle(file), icon, nick, codeRow);
    } else {
      const name = document.createElement("span");
      name.className = "file-name";
      name.textContent = file.name;
      const nick = document.createElement("span");
      nick.className = "file-nick";
      nick.textContent = `/${file.nickname}`;
      name.appendChild(nick);

      const meta = document.createElement("span");
      meta.className = "file-meta";
      meta.innerHTML = `<span class="row-date">${file.date}</span><span class="row-size">${file.size}</span>`;

      const uses = document.createElement("span");
      uses.className = "row-uses";
      uses.textContent = usesLabel(file);
      uses.title = Number.isFinite(file.shareLimit) ? `${file.sharesLeft} utilizações restantes` : "Utilizações ilimitadas";

      meta.append(uses, code, ring);
      item.append(makeSelectCircle(file), name, meta);
    }

    item._file = file;
    item._codeEl = code;
    item._ringEl = ring.querySelector(".ring-progress");
    collection.appendChild(item);
  }

  emptyState.hidden = files.length > 0;
  const allSelected = files.length > 0 && files.every((f) => state.selected.has(f.name));
  selectAll.classList.toggle("is-selected", allSelected);
  selectAll.setAttribute("aria-pressed", String(allSelected));
  updateTimers();
}

// Drives every code + ring from the shared window; rolls all codes at zero.
function updateTimers() {
  const now = Date.now();
  if (now >= windowEnd) {
    for (const f of state.files) f.code = randomCode(6);
    windowEnd = now + CODE_PERIOD_MS;
  }
  const offset = (RING_C * (1 - Math.max(0, windowEnd - now) / CODE_PERIOD_MS)).toFixed(2);
  for (const li of collection.children) {
    if (!li._file) continue;
    li._codeEl.textContent = li._file.code;
    li._ringEl.style.strokeDashoffset = offset;
  }
}
setInterval(updateTimers, 250);

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
  const allSelected = files.length > 0 && files.every((f) => state.selected.has(f.name));
  files.forEach((f) => (allSelected ? state.selected.delete(f.name) : state.selected.add(f.name)));
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

// A limit button both picks the allowance and confirms the upload.
// TODO(api): becomes POST /api/files — multipart upload with nickname + limit.
for (const btn of limitButtons) {
  btn.addEventListener("click", () => {
    const nickname = uploadNickname.value.trim().toLowerCase();
    if (!/^[a-z0-9-]{1,20}$/.test(nickname)) {
      uploadError.textContent = "O nickname deve ter 1–20 caracteres: letras, números ou hífens.";
      uploadError.hidden = false;
      return;
    }
    const limit = btn.dataset.limit === "inf" ? Infinity : parseInt(btn.dataset.limit, 10);

    const f = uploadQueue.shift();
    const ext = f.name.split(".").pop().toLowerCase();
    const type = ext === "pdf" ? "pdf"
      : ["png", "jpg", "jpeg", "gif", "webp"].includes(ext) ? "image"
      : "doc";
    state.files.unshift({
      name: f.name,
      type,
      size: `${Math.max(1, Math.round(f.size / 1024))} KB`,
      date: new Date().toISOString().slice(0, 10),
      nickname,
      shareLimit: limit,
      sharesLeft: limit,
      code: randomCode(6),
    });
    render();
    promptNextUpload();
  });
}

// Cancelar skips the current file (not added).
uploadCancel.addEventListener("click", () => {
  uploadQueue.shift();
  promptNextUpload();
});

render();
