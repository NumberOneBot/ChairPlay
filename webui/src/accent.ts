/**
 * Colours the page with ui.accent: every shade of the accent in the stylesheets (styles/base.css) is made from --accent, so setting it on
 * the root recolours both pages. Anything but #rrggbb leaves the stylesheet's own colour.
 */
export function applyAccent(accent: string | undefined) {
  if (accent && /^#[0-9a-fA-F]{6}$/.test(accent)) document.documentElement.style.setProperty('--accent', accent)
}

/** ui.tint's palettes (styles/tints.css); white is the light theme. */
const TINTS = ['black', 'graphite', 'slate', 'teal', 'white']

/** The default tint, and what a tint no palette has falls back to (owner, 2026-11-12). */
export const DEFAULT_TINT = 'black'

/** Greys both pages with ui.tint: the stylesheet keeps a palette for each data-tint on the root. */
export function applyTint(tint: string | undefined) {
  document.documentElement.dataset.tint = tint && TINTS.includes(tint) ? tint : DEFAULT_TINT
}
