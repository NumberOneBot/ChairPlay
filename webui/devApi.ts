// The dev server's way to the app (vite.config.ts): /api goes to ChairPlay on 127.0.0.1:[appPort], as if the page were
// the app's own. Without imports, so the page's tests and tsc (which keep to src/ and its imports) check it too.

/** The parts of http-proxy's request and server the proxy's configure hook uses. */
interface ProxyRequest {
  removeHeader(name: string): void
}
interface IncomingRequest {
  headers: { host?: string; origin?: string }
}
interface Proxy {
  on(event: 'proxyReq', listener: (proxyRequest: ProxyRequest, request: IncomingRequest) => void): unknown
}

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
        configure: dropOwnOrigin,
      },
    },
  }
}

/**
 * changeOrigin rewrites Host only, so the dev server's page would send its Origin (localhost:5173) on, which the app
 * refuses as another site's. Only that Origin is dropped; another site's page keeps its own, and the app refuses its
 * POSTs. Vite has refused a Host other than this PC's by then.
 */
function dropOwnOrigin(proxy: Proxy) {
  proxy.on('proxyReq', (proxyRequest, request) => {
    const { host, origin } = request.headers
    if (host !== undefined && origin === `http://${host}`) proxyRequest.removeHeader('origin')
  })
}
