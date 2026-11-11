import { useEffect, useId, useState } from 'react'
import type { AppState, Backend } from '../api'
import { AboutPage } from '../pages/AboutPage'
import { AltScreenPage } from '../pages/AltScreenPage'
import { AudioPage } from '../pages/AudioPage'
import { CarPlayPage } from '../pages/CarPlayPage'
import { ExperimentsPage } from '../pages/ExperimentsPage'
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
/** The phases in which a connection is being made, from the start to CarPlay's picture, a retry among them. */
const CONNECTING_PHASES = ['starting', 'calling', 'joining', 'retrying'] as const

export function Shell({ state, backend }: { state: AppState; backend: Backend }) {
  const [section, setSection] = useState<Section>('connection')
  // Get started shows on its own until the setup is done, unless skipped; Show again opens it whatever.
  const [guide, setGuide] = useState<'auto' | 'shown' | 'skipped'>('auto')
  const [joinFailureSeen, setJoinFailureSeen] = useState<string | null>(null)
  const ids = useId()
  const showable = state.canShowCarPlay && state.embedded
  const settings = useSettings(backend, state.settingsVersion)
  const guiding = guide === 'shown' || (guide === 'auto' && !state.setupDone)
  const steps = guiding ? setupSteps(state, joinFailureSeen) : null
  const showGuide = () => setGuide('shown')
  // The connection reads the CarPlay and AltScreen pages as it is made: changed meanwhile, which value it took is
  // anyone's guess. While it connects they wait, off; the pages that apply at once, and Connection, stay live.
  const connecting = (CONNECTING_PHASES as readonly string[]).includes(state.phase)
  const held = connecting && (section === 'carplay' || section === 'altscreen')

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
                onClick={() => setSection(id)}
              >
                {icon}
                <span className="label">{title}</span>
              </button>
            ))}
          </nav>
          <div className="page" role="tabpanel" id={`${ids}-page`} aria-labelledby={`${ids}-tab-${section}`}>
            <div className="page-inner">
              {settings.loadError && (
                <Message tone="bad" head="Couldn't read the settings." text={settings.loadError} />
              )}
              {settings.data && section === 'connection' && (
                <ConnectionPage state={state} backend={backend} settings={settings} onShowGuide={showGuide} />
              )}
              {/* Only around the pages it may hold: before any other, it would take its first group's place. */}
              {settings.data && (section === 'carplay' || section === 'altscreen') && (
                <div className={held ? 'held' : undefined} inert={held}>
                  {section === 'carplay' && <CarPlayPage state={state} backend={backend} settings={settings} />}
                  {section === 'altscreen' && <AltScreenPage state={state} backend={backend} settings={settings} />}
                </div>
              )}
              {settings.data && section === 'keyboard' && <KeyboardPage state={state} backend={backend} settings={settings} />}
              {settings.data && section === 'audio' && (
                <AudioPage state={state} backend={backend} settings={settings} />
              )}
              {settings.data && section === 'gps' && <GpsPage state={state} backend={backend} settings={settings} />}
              {settings.data && section === 'experiments' && (
                <ExperimentsPage state={state} backend={backend} settings={settings} />
              )}
              {section === 'about' && <AboutPage state={state} backend={backend} settings={settings} onShowGuide={showGuide} />}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
