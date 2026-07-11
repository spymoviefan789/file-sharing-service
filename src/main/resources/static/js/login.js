// QuickPrint login — front-end prototype.
// TODO(api): replace the fake submit with POST /api/auth/login.

const form = document.getElementById("login-form");
const errorBox = document.getElementById("login-error");

form.addEventListener("submit", (event) => {
  event.preventDefault();

  const email = form.email.value.trim();
  const password = form.password.value;

  if (!email || !password) {
    errorBox.textContent = "Preenche o email e a password.";
    errorBox.hidden = false;
    return;
  }

  errorBox.hidden = true;

  // TODO(api): fetch("/api/auth/login", { method: "POST", ... })
  // On success the server sets the session cookie and we go to the dashboard.
  window.location.href = "dashboard.html";
});
