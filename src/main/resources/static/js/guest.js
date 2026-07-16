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
