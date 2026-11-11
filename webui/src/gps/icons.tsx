// The USB GPS screens' icons, each the mock's own drawing as it is (desktop-docs/mockups/usb-gps: views.js,
// experiments.js, and track-playback.js), its attributes written for JSX.

/** A treasure map's route, a dashed way from a start dot to a cross, on Open… (the mock's trackIcon). */
export function TrackIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path
        d="M4.4 13.6C6.5 13.6 8 14.4 10.4 13.9C13.6 13.1 13.4 8.9 10.4 8.7C8.6 8.6 7.4 9.8 5.6 9.4C2.4 8.7 2.6 4.1 5.6 3.6C7.2 3.4 8.6 4.1 10.2 3.8"
        fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeDasharray="1.8 1.6"
      />
      <circle cx="2.9" cy="13.6" r="1.8" fill="currentColor" />
      <path d="M11.3 1.5l3.2 3.2M14.5 1.5l-3.2 3.2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}

/** A square, the track's Stop key (the mock's stop glyph). */
export function StopIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <rect x="3.5" y="3.5" width="9" height="9" fill="currentColor" />
    </svg>
  )
}

/** The USB trident, on USB setup (the mock's usbIcon). */
export function UsbIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M8 12V3M8 10 4 7V5M8 8l4-3V4" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="m8 1-2 3h4z" fill="currentColor" />
      <circle cx="4" cy="4" r="1.4" fill="currentColor" />
      <rect x="10.7" y="1.5" width="2.6" height="2.6" fill="currentColor" />
      <circle cx="8" cy="13.2" r="1.8" fill="currentColor" />
    </svg>
  )
}

/** The Microsoft Store's bag with the Windows logo, on Get Apple Devices (the mock's storeIcon). */
export function StoreIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M2.5 5h11v9h-11zM5 5V3.8a3 3 0 0 1 6 0V5" fill="none" stroke="currentColor" strokeWidth="1.2" />
      <path d="M5.5 7h2v2h-2zm3 0h2v2h-2zm-3 3h2v2h-2zm3 0h2v2h-2z" fill="currentColor" />
    </svg>
  )
}

/** A phone, on the Developer tools folder and Open Apple Devices (the mock's phoneIcon). */
export function PhoneIcon() {
  return (
    <svg viewBox="0 0 14 14" aria-hidden="true">
      <rect x="3.5" y="1" width="7" height="12" rx="1.4" fill="none" stroke="currentColor" strokeWidth="1.2" />
      <path d="M5.8 2.5h2.4M5.8 11.3h2.4" stroke="currentColor" strokeWidth="1.1" strokeLinecap="round" />
    </svg>
  )
}

/** A triangle with "!", a step's error line (the mock's errorIcon). */
export function ErrorIcon() {
  return (
    <svg className="error-icon" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M8 1.5 14.5 13H1.5Z" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
      <path d="M8 5.5v3.7" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
      <circle cx="8" cy="11.2" r=".7" fill="currentColor" />
    </svg>
  )
}

/** An arrow to the left, the way out of the setup (the mock's usb-back arrow). */
export function BackIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M13 8H3m4-4L3 8l4 4" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
