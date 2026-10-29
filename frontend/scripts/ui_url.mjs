/** Parse an absolute browser URL without shadowing the built-in URL constructor.
 * Keep this pure so CI can test routing diagnostics without starting Playwright.
 */
export function pathnameFromUrl(absoluteUrl) {
  return new URL(absoluteUrl).pathname;
}
