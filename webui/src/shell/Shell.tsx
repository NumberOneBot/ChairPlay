import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import type { AppState, Backend } from '../api'
import { AltScreenPage } from '../pages/AltScreenPage'
import { AboutPage } from '../pages/AboutPage'
import { ApplicationPage } from '../pages/ApplicationPage'
import { AudioPage } from '../pages/AudioPage'
import { CarPlayPage } from '../pages/CarPlayPage'
import { GpsPage } from '../pages/GpsPage'
import { ConnectionPage } from '../pages/ConnectionPage'
import { KeyboardPage } from '../pages/KeyboardPage'
import { GetStarted } from '../setup/GetStarted'
import { setupSteps } from '../setup/steps'
import { Message } from './controls'
import { SECTIONS, type Section } from './sections'
import { Toolbar } from './Toolbar'
import { useKeyboardFocus } from './useKeyboardFocus'
import { useKeysToApp } from './useKeysToApp'
import { useSettings } from './useSettings'
import { Waiting } from './Waiting'

export type { Section } from './sections'

/**
 * The main window's page whenever CarPlay does not cover it: the toolbar, the section list, and the chosen
 * section's settings, or Get started in their place until an iPhone has connected, and again from Show again. Escape
 * shows CarPlay when it can, unless a page used the key itself.
 */
export function Shell({ state, backend }: { state: AppState; backend: Backend }) {
  const [section, setSection] = useState<Section>('connection')
  const page = useRef<HTMLDivElement>(null)
  // A tab opens its page; the tab already open takes its page back to the top (owner, 2026-11-12).
  const choose = (id: Section) => {
    if (id === section) page.current?.scrollTo?.({ top: 0, behavior: 'smooth' })
    else setSection(id)
  }
  // Every page opens at its top, however the one before it was scrolled (owner, 2026-11-12).
  useLayoutEffect(() => {
    if (page.current) page.current.scrollTop = 0
  }, [section])
  // Get started shows on its own until the setup is done, unless skipped; Show again opens it whatever.
  const [guide, setGuide] = useState<'auto' | 'shown' | 'skipped'>('auto')
  const [joinFailureSeen, setJoinFailureSeen] = useState<string | null>(null)
  const ids = useId()
  const showable = state.canShowCarPlay && state.embedded
  const settings = useSettings(backend, state.settingsVersion)
  const guiding = guide === 'shown' || (guide === 'auto' && !state.setupDone)
  const steps = guiding ? setupSteps(state, joinFailureSeen) : null
  const showGuide = () => setGuide('shown')
  // Every page stays open while the iPhone connects: each attempt tells the app which values it read (UiSettings
  // .connected), so a change it has already read is marked to wait for the next connection (owner, 2026-11-12).

  // Forget this iPhone undoes the setup: Get started comes back, though it was skipped before.
  useEffect(() => {
    if (!state.setupDone) setGuide((current) => (current === 'skipped' ? 'auto' : current))
  }, [state.setupDone])

  // F11, F12 and the app's other keys work over the settings as over CarPlay.
  useKeysToApp(backend, false)
  useKeyboardFocus(backend, state.controls?.globalKeyboard?.enabled === true)

  useEffect(() => {
    if (!showable) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !event.defaultPrevented) backend.show('carplay')
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [backend, showable])

  // Connecting on its own the app waits, black, instead of showing the settings, until CarPlay covers it.
  if (state.waiting) return <Waiting state={state} backend={backend} />

  // CarPlay is to show: the window hides the page a fade's time later (Fade.kt), so it fades to black meanwhile.
  const away = state.embedded && state.screen === 'carplay'

  return (
    <div className={`shell ${away ? 'away' : ''}`}>
      <Toolbar state={state} backend={backend} guide={steps} />
      {steps ? (
        <div className="body setup-body">
          {settings.data && (
            <GetStarted
              state={state}
              backend={backend}
              settings={settings}
              steps={steps}
              onPasswordSaved={() => setJoinFailureSeen(state.error?.at ?? null)}
              onSkip={() => setGuide('skipped')}
            />
          )}
        </div>
      ) : (
        <div className="body">
          <nav className="nav" role="tablist" aria-orientation="vertical" aria-label="Settings">
            {SECTIONS.map(({ id, title, icon }) => (
              <button
                key={id}
                type="button"
                role="tab"
                id={`${ids}-tab-${id}`}
                aria-controls={`${ids}-page`}
                aria-selected={section === id}
                className={section === id ? 'sel' : ''}
                aria-label={title}
                title={title}
                data-testid={`tab-${id}`}
                onClick={() => choose(id)}
              >
                {icon}
                <span className="label">{title}</span>
              </button>
            ))}
          </nav>
          <div ref={page} className="page" role="tabpanel" id={`${ids}-page`} aria-labelledby={`${ids}-tab-${section}`}>
            <div className="page-inner">
              {settings.loadError && (
                <Message tone="bad" head="Couldn't read the settings." text={settings.loadError} />
              )}
              {settings.data && section === 'connection' && (
                <ConnectionPage state={state} backend={backend} settings={settings} onShowGuide={showGuide} />
              )}
              {settings.data && section === 'carplay' && <CarPlayPage state={state} backend={backend} settings={settings} />}
              {settings.data && section === 'altscreen' && (
                <AltScreenPage state={state} backend={backend} settings={settings} />
              )}
              {settings.data && section === 'keyboard' && <KeyboardPage state={state} backend={backend} settings={settings} />}
              {settings.data && section === 'audio' && (
                <AudioPage state={state} backend={backend} settings={settings} />
              )}
              {settings.data && section === 'gps' && <GpsPage state={state} backend={backend} settings={settings} />}
              {settings.data && section === 'application' && (
                <ApplicationPage state={state} backend={backend} settings={settings} />
              )}
              {section === 'about' && (
                <AboutPage state={state} backend={backend} settings={settings} onShowGuide={showGuide} />
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
