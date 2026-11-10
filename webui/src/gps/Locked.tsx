import type { ReactNode } from 'react'

/**
 * While an operation holds the window, what is inside neither takes the pointer nor the keys (inert) and tells assistive
 * technology it is busy; the operation's own block stays outside, readable.
 */
export function Locked({ locked, children }: { locked: boolean; children: ReactNode }) {
  return (
    <div className="locked" inert={locked} aria-busy={locked || undefined}>
      {children}
    </div>
  )
}
