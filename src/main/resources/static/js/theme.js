// Light/dark theme: follow the OS by default, let the user override, remember it.
// Loaded in <head> so the saved choice applies before first paint (no flash).
(function () {
  var root = document.documentElement;
  var saved = localStorage.getItem("qp-theme"); // "light" | "dark" | null (=follow OS)
  if (saved) root.setAttribute("data-theme", saved);

  function effective() {
    return (
      root.getAttribute("data-theme") ||
      (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")
    );
  }

  // Show the icon of the mode you'd switch TO.
  function paint(btn) {
    btn.textContent = effective() === "dark" ? "☀" : "☾"; // ☀ / ☾
    btn.setAttribute("aria-label", effective() === "dark" ? "Mudar para modo claro" : "Mudar para modo escuro");
  }

  window.addEventListener("DOMContentLoaded", function () {
    var btn = document.querySelector("[data-theme-toggle]");
    if (!btn) return;
    paint(btn);
    btn.addEventListener("click", function () {
      var next = effective() === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      localStorage.setItem("qp-theme", next);
      paint(btn);
    });
  });
})();
