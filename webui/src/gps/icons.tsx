// The USB GPS screens' icons, 16 px outlines in the current color like the app's others (shell/icons.tsx).

/** The USB trident, on USB setup. */
export function UsbIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <g fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round">
        <path d="M8 14V3" />
        <path d="M8 3 6.6 4.6h2.8z" fill="currentColor" />
        <path d="M8 10.5 4.5 8.2v-1.5" />
        <rect x="3.3" y="4.6" width="2.4" height="2.1" />
        <path d="M8 8.6l3-1.9v-1" />
        <circle cx="11" cy="4.6" r="1.1" />
        <circle cx="8" cy="14" r="1.1" fill="currentColor" />
      </g>
    </svg>
  )
}

/** A shopping bag, the Microsoft Store's sign, on Get Apple Devices. */
export function StoreIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <g fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" strokeLinecap="round">
        <path d="M2.6 5h10.8l-.7 8.4H3.3z" />
        <path d="M5.5 5V4a2.5 2.5 0 0 1 5 0v1" />
      </g>
    </svg>
  )
}

/** A phone in outline, on the Developer tools folder. */
export function PhoneIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <g fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round">
        <rect x="4.2" y="1.6" width="7.6" height="12.8" rx="1.8" />
        <path d="M7 12.2h2" />
      </g>
    </svg>
  )
}

/** A triangle with "!", a step's error line, as large as the information sign (InfoIcon). */
export function ErrorIcon() {
  return (
    <svg className="error-icon" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M8 1.9 14.6 13.4H1.4z" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" />
      <path d="M8 6.4v3.3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      <circle cx="8" cy="11.5" r="0.85" fill="currentColor" />
    </svg>
  )
}

/** An arrow to the left, the way out of the setup. */
export function BackIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path
        d="M13 8H3.2M7.2 4 3.2 8l4 4"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
