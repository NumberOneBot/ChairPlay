import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The dev server (npm run dev) sends the API to a running ChairPlay, started with CHAIRPLAY_UI_PORT set to the
// same port; open http://localhost:5173/.
const appPort = process.env.CHAIRPLAY_UI_PORT ?? '47800'

// The app's font files, which the HUD (HudStyle.kt) draws with and the stylesheet takes as @fonts/<file>: the build
// copies the ones it uses into its assets, so the repository holds each once.
const fonts = fileURLToPath(new URL('../src/main/resources/fonts', import.meta.url))
// The window icons, which the About page shows as @icons/<file>, for the same reason.
const icons = fileURLToPath(new URL('../src/main/resources/icons', import.meta.url))

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@fonts': fonts, '@icons': icons },
  },
  build: {
    // Gradle passes --outDir build/webui/resources/ui, which goes into the jar.
    outDir: 'dist',
    assetsDir: 'assets',
    target: 'es2022',
  },
  server: {
    port: 5173,
    strictPort: true,
    // The fonts and icons live outside webui/, which the dev server otherwise keeps to.
    fs: { allow: ['.', fonts, icons] },
    proxy: {
      // changeOrigin sends the app its own host name, which it insists on. It rewrites Host only, so the browser's
      // Origin (localhost:5173) is dropped here; the app would refuse every POST with it as another site's.
      '/api': {
        target: `http://127.0.0.1:${appPort}`,
        changeOrigin: true,
        configure: (proxy) => proxy.on('proxyReq', (request) => request.removeHeader('origin')),
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
