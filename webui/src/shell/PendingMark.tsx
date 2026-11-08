// The marks of a change that waits: for ChairPlay's restart, a power sign; for the next connection, ⟳. A restart's
// mark is never ⟳ or the Reconnect button's sign: the owner took one for the other, and Reconnect applied nothing.

/** The power sign, a ring open at the top with a stroke down into it, in the mark's color. */
export function RestartIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      {/* 1.2 matches the ⟳ beside it, a glyph of the UI font, at the marks' size */}
      <g fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round">
        <path d="M4.9 4.1a5.4 5.4 0 1 0 6.2 0" />
        <path d="M8 1.8v5.6" />
      </g>
    </svg>
  )
}

/** A mark that a change waits for [what]; [label] names it, and [title] (else [label]) is its tooltip. */
export function PendingMark(props: { what: 'restart' | 'reconnect'; label: string; title?: string; className: string }) {
  return (
    <span
      className={props.className}
      role="img"
      aria-label={props.label}
      title={props.title ?? props.label}
      data-testid={`${props.what}-mark`}
    >
      {props.what === 'restart' ? <RestartIcon /> : '⟳'}
    </span>
  )
}
