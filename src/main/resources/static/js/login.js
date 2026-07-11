// QuickPrint login — posts to Spring Security's form-login endpoint.
// URLSearchParams makes the browser send application/x-www-form-urlencoded,
// which is what the form-login filter expects (fields: email, password).

const form = document.getElementById("login-form");
const errorBox = document.getElementById("login-error");

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  const email = form.email.value.trim();
  const password = form.password.value;

  if (!email || !password) {
    errorBox.textContent = "Preenche o email e a password.";
    errorBox.hidden = false;
    return;
  }

  errorBox.hidden = true;

  const response = await fetch("/api/auth/login", {
    method: "POST",
    body: new URLSearchParams({ email, password }),
  });

  if (response.ok) {
    // The session cookie is set; go to the dashboard.
    window.location.href = "dashboard.html";
  } else {
    errorBox.textContent = "Email ou password incorretos.";
    errorBox.hidden = false;
  }
});
