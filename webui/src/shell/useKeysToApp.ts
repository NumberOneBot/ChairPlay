import { useEffect } from 'react'
import type { Backend, KeyPhase } from '../api'
import { bindingName, isModifier } from '../pages/keyNames'
import { useKeyboardFocus } from './useKeyboardFocus'

/** Whether [target] takes what is typed: a field, a list or editable text. */
function typesHere(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
}

/**
 * A page has the keyboard while it shows, so the window's keys would never reach the app: they are handed on, as
 * pressed on the window ([Backend.key]). [everything] over the controls window, which has nothing to type in or move
 * about; over the settings the app takes only its own keys. A key the page used (preventDefault) is the page's, and
 * so is a character typed in a field.
 */
export function useKeysToApp(backend: Backend, everything: boolean) {
  useKeyboardFocus(backend)
  useEffect(() => {
    const held = new Map<string, string>()
    const release = () => {
      for (const name of held.values()) void backend.key(name, 'release', everything)
      held.clear()
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.type === 'keyup') {
        const name = held.get(event.code)
        if (!name) return
        held.delete(event.code)
        if (everything) event.preventDefault()
        void backend.key(name, 'release', everything)
        return
      }
      if (event.defaultPrevented || isModifier(event.code)) return
      if (typesHere(event.target) && event.key.length === 1 && !event.ctrlKey && !event.altKey) return
      const name = bindingName(event)
      if (!name) return
      const phase: KeyPhase = event.repeat ? 'repeat' : 'press'
      if (phase === 'press') held.set(event.code, name)
      // Over the controls window a key is the app's alone: Tab does not also move the focus, nor Enter press a button.
      if (everything) event.preventDefault()
      void backend.key(name, phase, everything)
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('keyup', onKey)
    window.addEventListener('blur', release)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('keyup', onKey)
      window.removeEventListener('blur', release)
      release()
    }
  }, [backend, everything])
}
