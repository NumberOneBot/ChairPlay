// The dev server's way to the app (vite.config.ts): /api goes to ChairPlay on 127.0.0.1:[appPort], as if the page were
// the app's own. Without imports, so the page's tests and tsc (which keep to src/ and its imports) check it too.

export function devApi(appPort: string) {
  return {
    // The page is the dev server's own, so it needs no CORS; Vite's default would let a page on any other localhost
    // port read the answers, the Wi-Fi password among them.
    cors: false as const,
    proxy: {
      '/api': {
        target: `http://127.0.0.1:${appPort}`,
        // changeOrigin sends the app its own host name, which it insists on.
        changeOrigin: true,
      },
    },
  }
}
