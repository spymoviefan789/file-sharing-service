// QuickPrint receiving page — enter nickname + code, then preview or download.
// The file comes back in the POST response body; we turn it into a blob so the
// browser can save it (download) or open it (preview). The code never goes in
// the URL.

const form = document.getElementById("receive-form");
const nicknameInput = document.getElementById("nickname");
const codeInput = document.getElementById("code");
const errorBox = document.getElementById("receive-error");
const previewBtn = document.getElementById("preview-btn");

// If already logged in, the corner link should lead back to the dashboard
// rather than to a login the user has already done. (No corner link on the
// dashboard, where this same script also runs the sidebar receive widget.)
const cornerLink = document.getElementById("corner-link");
if (cornerLink) {
  fetch("/api/auth/me")
    .then((res) => {
      if (!res.ok) return;
      cornerLink.textContent = "O MEU PAINEL";
      cornerLink.href = "dashboard.html";
    })
    .catch(() => {});
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  receive("download");
});

previewBtn.addEventListener("click", () => receive("preview"));

async function receive(mode) {
  const nickname = nicknameInput.value.trim().toLowerCase();
  const code = codeInput.value.trim().toUpperCase();

  if (!nickname || !code) {
    showError("Preenche o nome do ficheiro e o código.");
    return;
  }
  errorBox.hidden = true;

  const response = await fetch(`/api/receive/${encodeURIComponent(nickname)}`, {
    method: "POST",
    body: new URLSearchParams({ code, mode }),
  });

  if (response.status === 404) {
    showError("Não encontrado — o nome ou o código estão errados, ou já expirou.");
    return;
  }
  if (response.status === 415) {
    showError("Este ficheiro não pode ser pré-visualizado. Usa DESCARREGAR.");
    return;
  }
  if (!response.ok) {
    showError("Algo correu mal. Tenta novamente.");
    return;
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);

  if (mode === "preview") {
    window.open(url, "_blank", "noopener");
    // Give the new tab a moment to load before releasing the object URL.
    setTimeout(() => URL.revokeObjectURL(url), 10000);
  } else {
    const link = document.createElement("a");
    link.href = url;
    link.download = filenameFrom(response.headers.get("Content-Disposition")) || nickname;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }
}

// Pulls the filename out of a Content-Disposition header.
function filenameFrom(header) {
  if (!header) return null;
  const utf8 = header.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8) return decodeURIComponent(utf8[1]);
  const plain = header.match(/filename="?([^";]+)"?/i);
  return plain ? plain[1] : null;
}

function showError(message) {
  errorBox.textContent = message;
  errorBox.hidden = false;
}
