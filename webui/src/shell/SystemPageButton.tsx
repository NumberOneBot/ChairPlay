import type { ReactNode } from 'react'
import type { Backend, WindowsPlace } from '../api'
import { opens } from '../i18n/platform'

/** A button that opens the system's own [place] page; nothing where the system has no such page. */
export function SystemPageButton({ place, backend, title, className = 'btn small', testId, children }: {
  place: WindowsPlace
  backend: Pick<Backend, 'open'>
  title?: string
  className?: string
  testId?: string
  children: ReactNode
}) {
  if (!opens(place)) return null
  return (
    <button type="button" className={className} title={title} data-testid={testId ?? `open-${place}`}
      onClick={() => void backend.open(place)}>
      {children}
    </button>
  )
}
