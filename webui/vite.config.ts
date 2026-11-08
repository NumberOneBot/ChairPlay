import { fileURLToPath } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { devApi } from './devApi.ts'

// The dev server (npm run dev) sends the API to a running ChairPlay, on ui.port's default 7529 or, when set, on
// CHAIRPLAY_UI_PORT, which the app takes too; open http://localhost:5173/.
const appPort = process.env.CHAIRPLAY_UI_PORT ?? '7529'

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
    ...devApi(appPort),
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
