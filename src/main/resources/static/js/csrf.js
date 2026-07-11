// Reads the CSRF token Spring writes to the (JS-readable) XSRF-TOKEN cookie, so
// we can echo it back in the X-XSRF-TOKEN header on state-changing requests.
// Spring compares the two; a forged cross-site request can't read the cookie,
// so it can't produce the header.
function csrfHeaders() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? { "X-XSRF-TOKEN": decodeURIComponent(match[1]) } : {};
}
