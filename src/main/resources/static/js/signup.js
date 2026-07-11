// QuickPrint sign up — POST /api/auth/register, then send the user to log in.

const form = document.getElementById("signup-form");
const errorBox = document.getElementById("signup-error");

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  const email = form.email.value.trim();
  const password = form.password.value;

  if (!email || password.length < 8) {
    errorBox.textContent = "Preenche o email e uma password com pelo menos 8 caracteres.";
    errorBox.hidden = false;
    return;
  }

  errorBox.hidden = true;

  const response = await fetch("/api/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...csrfHeaders() },
    body: JSON.stringify({ email, password }),
  });

  if (response.status === 201) {
    window.location.href = "login.html";
  } else if (response.status === 409) {
    errorBox.textContent = "Já existe uma conta com esse email.";
    errorBox.hidden = false;
  } else {
    errorBox.textContent = "Não foi possível criar a conta. Verifica os dados e tenta de novo.";
    errorBox.hidden = false;
  }
});
