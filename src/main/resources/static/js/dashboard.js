// QuickPrint dashboard — front-end prototype.
// Everything here runs on mock data; each "TODO(api)" marks where a real
// backend call will replace it once the REST endpoints exist.

// TODO(api): replace with GET /api/files (the owner's uploaded files)
const mockFiles = [
  { name: "Relatorio_Circuitos.pdf", type: "pdf", size: "1.2 MB", date: "2026-07-08" },
  { name: "apontamentos_sinais.docx", type: "doc", size: "340 KB", date: "2026-07-05" },
  { name: "horario_2026.png", type: "image", size: "88 KB", date: "2026-06-30" },
  { name: "guia_laboratorio.pdf", type: "pdf", size: "2.8 MB", date: "2026-06-21" },
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

function visibleFiles() {
  return state.files
    .filter((f) => f.name.toLowerCase().includes(state.search))
    .filter((f) => state.type === "all" || f.type === state.type)
    .sort((a, b) =>
      state.sortDesc ? b.date.localeCompare(a.date) : a.date.localeCompare(b.date)
    );
}

function render() {
  const files = visibleFiles();

  collection.className = state.view === "grid" ? "files files--grid" : "files files--list";
  collection.innerHTML = "";

  for (const file of files) {
    const item = document.createElement("li");
    item.className = "file";

    const circle = document.createElement("button");
    circle.type = "button";
    circle.className = "select-circle";
    circle.setAttribute("aria-label", `Selecionar ${file.name}`);
    circle.setAttribute("aria-pressed", String(state.selected.has(file.name)));
    if (state.selected.has(file.name)) circle.classList.add("is-selected");
    circle.addEventListener("click", () => {
      state.selected.has(file.name)
        ? state.selected.delete(file.name)
        : state.selected.add(file.name);
      render();
    });

    const name = document.createElement("span");
    name.className = "file-name";
    name.textContent = file.name;

    const meta = document.createElement("span");
    meta.className = "file-meta";
    meta.innerHTML = `<span>${file.size}</span><span>${file.date}</span>`;

    item.append(circle, name, meta);
    collection.appendChild(item);
  }

  emptyState.hidden = files.length > 0;

  const allSelected = files.length > 0 && files.every((f) => state.selected.has(f.name));
  selectAll.classList.toggle("is-selected", allSelected);
  selectAll.setAttribute("aria-pressed", String(allSelected));
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
  const allSelected = files.length > 0 && files.every((f) => state.selected.has(f.name));
  files.forEach((f) =>
    allSelected ? state.selected.delete(f.name) : state.selected.add(f.name)
  );
  render();
});

addFile.addEventListener("click", () => fileInput.click());

// TODO(api): replace with POST /api/files (multipart upload)
fileInput.addEventListener("change", () => {
  for (const f of fileInput.files) {
    const ext = f.name.split(".").pop().toLowerCase();
    const type = ext === "pdf" ? "pdf"
      : ["png", "jpg", "jpeg", "gif", "webp"].includes(ext) ? "image"
      : "doc";
    state.files.unshift({
      name: f.name,
      type,
      size: `${Math.max(1, Math.round(f.size / 1024))} KB`,
      date: new Date().toISOString().slice(0, 10),
    });
  }
  fileInput.value = "";
  render();
});

render();
