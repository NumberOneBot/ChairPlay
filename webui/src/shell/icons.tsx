// The toolbar's and the section list's icons, drawn as in desktop-docs/mockups/desktop-ui.html.
import { useId } from 'react'

/** A mask id of the icon's own: two copies of one icon on a page must not share one. */
function useMaskId(name: string) {
  return `${name}-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
}

/** CarPlay's own home button: a light disc with a rounded square drawn out of it. */
export function CarPlayIcon() {
  const mask = useMaskId('carplay')
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <defs>
        <mask id={mask}>
          <rect width="16" height="16" fill="#fff" />
          <rect x="5.1" y="5.1" width="5.8" height="5.8" rx="1.6" fill="none" stroke="#000" strokeWidth="1.1" />
        </mask>
      </defs>
      <circle cx="8" cy="8" r="7.3" fill="currentColor" mask={`url(#${mask})`} />
    </svg>
  )
}

/**
 * An instrument cluster: a long panel in outline, as the section list drew it before, with the speedometer and the rev
 * counter filled and a pixel in from its ends (owner, 2026-11-12)
 */
export function AltScreenIcon() {
  return (
    <svg className="wide" viewBox="0 0 26 16" aria-hidden="true">
      <rect x="0.8" y="2.2" width="24.4" height="11.6" rx="3.6" fill="none" stroke="currentColor" strokeWidth="1.2" />
      <circle cx="6.7" cy="8" r="3.9" fill="currentColor" />
      <circle cx="19.3" cy="8" r="3.9" fill="currentColor" />
    </svg>
  )
}

/** A filled knob with its pointer cut out, inside a scale: the controls window. */
export function ControlsIcon() {
  const mask = useMaskId('pad')
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <defs>
        <mask id={mask}>
          <rect width="16" height="16" fill="#fff" />
          <path d="M8 5.6V8.8" stroke="#000" strokeWidth="1.3" strokeLinecap="round" />
        </mask>
      </defs>
      <path
        d="M2.7 13.4A6.9 6.9 0 1 1 13.3 13.4"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinecap="round"
      />
      <circle cx="8" cy="9" r="4.6" fill="currentColor" mask={`url(#${mask})`} />
    </svg>
  )
}

export function ReconnectIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <path d="M11.5 7A4.5 4.5 0 1 1 9.8 3.5" fill="none" stroke="currentColor" strokeWidth="1.4" />
      <path d="M9 1.5l2 2.2-2.6.9z" fill="currentColor" />
    </svg>
  )
}

/** Power off, in the manner of Material's PowerOff: a filled plug struck through by a diagonal with a gap round it. */
export function DisconnectIcon() {
  const mask = useMaskId('unplug')
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <defs>
        <mask id={mask}>
          <rect width="16" height="16" fill="#fff" />
          <path d="M2.2 1.4 14.6 13.8" stroke="#000" strokeWidth="4.4" />
        </mask>
      </defs>
      <g mask={`url(#${mask})`}>
        <path d="M5.6 1.4v3M10.4 1.4v3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
        <path d="M3.6 4.4h8.8v2.8a4.4 4.4 0 0 1-3.4 4.3v3.1H7v-3.1A4.4 4.4 0 0 1 3.6 7.2z" fill="currentColor" />
      </g>
      <path d="M2.2 1.4 14.6 13.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

export function WifiIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <path
        d="M1.5 5.5a8 8 0 0 1 11 0M3.5 7.8a5 5 0 0 1 7 0M5.5 10a2 2 0 0 1 3 0"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
      />
    </svg>
  )
}

/** The CarPlay home button in lines, like the other section icons. */
export function CarPlayLineIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <circle cx="8" cy="8" r="6.6" fill="none" stroke="currentColor" strokeWidth="1.2" />
      <rect x="5.2" y="5.2" width="5.6" height="5.6" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.2" />
    </svg>
  )
}

/**
 * The AltScreen in the section list: a gauge, the speed glyph of the track playback mock
 * (desktop-docs/mockups/usb-gps/track-playback.js) as it is; the toolbar's is the cluster with filled gauges.
 */
export function AltScreenLineIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M2.6 12A6 6 0 1 1 13.4 12" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      <path d="M8 10.2l3-3.7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <circle cx="8" cy="10.2" r="1.3" fill="currentColor" />
    </svg>
  )
}

export function KeyboardIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <rect x="1" y="3.5" width="12" height="7" rx="1" fill="none" stroke="currentColor" strokeWidth="1.3" />
      <path d="M3.5 6h1M6.5 6h1M9.5 6h1M4.5 8.3h5" stroke="currentColor" strokeWidth="1.2" />
    </svg>
  )
}

export function AudioIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <path d="M2 5h2.5L8 2v10L4.5 9H2z" fill="none" stroke="currentColor" strokeWidth="1.3" />
      <path d="M10 5a3 3 0 0 1 0 4" fill="none" stroke="currentColor" strokeWidth="1.3" />
    </svg>
  )
}

/** A flask: the Experiments page. */
/** The GPS: a navigation arrow, as a map draws the car, its outline in the line icons' stroke. */
export function GpsIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <path
        d="M7 1.6L11.6 12.2L7 9.6L2.4 12.2Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
      />
    </svg>
  )
}

export function FlaskIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <path
        d="M5.3 1.6h3.4M5.8 1.6v3.9L2.4 11.2a.9.9 0 0 0 .8 1.3h7.6a.9.9 0 0 0 .8-1.3L8.2 5.5V1.6"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <path d="M3.9 8.9h6.2" stroke="currentColor" strokeWidth="1.1" />
    </svg>
  )
}

export function AboutIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <circle cx="7" cy="7" r="5.5" fill="none" stroke="currentColor" strokeWidth="1.3" />
      <path d="M7 6.2v4" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="7" cy="4.2" r=".9" fill="currentColor" />
    </svg>
  )
}

/** A page with a folded corner and lines of text: the logs folder. */
export function LogsIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <path
        d="M3 1.5h5.3L11 4.2v8.3H3zM8.3 1.5v2.7H11"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
      />
      <path d="M5 7h4M5 9.3h4" stroke="currentColor" strokeWidth="1.1" />
    </svg>
  )
}

/** A framed picture of hills under a sun: the screenshots folder. */
export function ScreenshotsIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <rect x="1.2" y="2.4" width="11.6" height="9.2" rx="1" fill="none" stroke="currentColor" strokeWidth="1.3" />
      <path
        d="M1.8 10.6l3.4-3.5 2.4 2.4 1.6-1.6 3 3"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinejoin="round"
      />
      <circle cx="9.6" cy="5.3" r="1" fill="none" stroke="currentColor" strokeWidth="1.1" />
    </svg>
  )
}

export function EyeIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path
        d="M1.5 8s2.4-4.5 6.5-4.5S14.5 8 14.5 8 12.1 12.5 8 12.5 1.5 8 1.5 8z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
      />
      <circle cx="8" cy="8" r="2" fill="currentColor" />
    </svg>
  )
}

/** A pencil, on a button that opens something done for a change. */
export function PencilIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path
        d="M10.6 2.6l2.8 2.8-7.9 7.9-3.5.7.7-3.5z M9.2 4l2.8 2.8"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.3"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** An "i" in a circle before a hint that heads off a common mistake. */
export function InfoIcon() {
  return (
    <svg className="info" viewBox="0 0 16 16" aria-hidden="true">
      <circle cx="8" cy="8" r="6.6" fill="none" stroke="currentColor" strokeWidth="1.3" />
      <path d="M8 7.2v4.3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      <circle cx="8" cy="4.9" r="0.85" fill="currentColor" />
    </svg>
  )
}

/** A globe in outline, the web's sign, on Open in Settings browser: its rim, a meridian, and the equator. */
export function BrowserIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <g fill="none" stroke="currentColor" strokeWidth="1.3">
        <circle cx="8" cy="8" r="6.6" />
        <ellipse cx="8" cy="8" rx="2.8" ry="6.6" />
        <path d="M1.4 8h13.2" />
      </g>
    </svg>
  )
}

/** A small padlock after a key that cannot be moved or removed. */
export function LockIcon() {
  return (
    <svg viewBox="0 0 10 10" aria-hidden="true">
      <rect x="1.5" y="4.5" width="7" height="5" rx="1" fill="currentColor" />
      <path d="M3 4.5V3a2 2 0 0 1 4 0v1.5" fill="none" stroke="currentColor" strokeWidth="1.2" />
    </svg>
  )
}

/** Windows' shield, on a button that asks for the administrator's consent: blue and yellow quarters. */
export function ShieldIcon() {
  return (
    <svg className="shield" viewBox="0 0 12 14" aria-hidden="true">
      <path d="M6 .8 11 2.6V6.6c0 3.2-2.1 5.6-5 6.6C3.1 12.2 1 9.8 1 6.6V2.6z" fill="#2f6fd6" />
      <path d="M6 .8V7H1V2.6zM6 7h5c-.2 2.9-2.2 5.2-5 6.2z" fill="#f5c400" />
    </svg>
  )
}
