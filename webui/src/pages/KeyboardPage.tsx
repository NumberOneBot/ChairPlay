import { useEffect, useState } from 'react'
import type { AppState, Backend, ControlsData } from '../api'
import { keyLabel } from '../ControlsScreen'
import { Group, Message, Row, Segmented, Switch } from '../shell/controls'
import { Field } from '../shell/fields'
import { LockIcon } from '../shell/icons'
import type { Settings } from '../shell/useSettings'
import { bindingName, isModifier } from './keyNames'
import { TextInputSettings } from './TextInputSettings'
import { GlobalShortcuts } from './GlobalShortcuts'
import { KEY_GROUPS } from './keyGroups'

/** Why a fixed key stays where it is. */
const FIXED_REASON: Record<string, string> = { escape: 'Esc always goes back', enter: 'Enter always selects' }

/** Up to three keys an action: the + goes at three. */
const MOST_KEYS = 3

/** A keycap waiting for the next key: in place of the one at [index], or after the last when it is null. */
interface Wait {
  action: string
  index: number | null
}

/**
 * The key bindings of controls.properties, the wheel, and the key repeat. Changes apply at once. A keycap clicked waits
 * for the next key and takes it; + waits for one more; a right-click removes a key. A key another action has moves
 * here; Esc cancels the wait; Escape always goes back and Enter always selects, so their keycaps are locked.
 */
export function KeyboardPage({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const [data, setData] = useState<ControlsData | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [wait, setWait] = useState<Wait | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const load = () =>
    backend.keyBindings().then((controls) => {
      setData(controls)
      setLoaded(true)
    })

  useEffect(() => {
    void load()
  }, [backend])

  async function save(changes: Record<string, string>) {
    const outcome = await backend.saveKeyBindings(changes)
    const keys = Object.keys(changes)
    setErrors((current) => {
      const kept = Object.fromEntries(Object.entries(current).filter(([key]) => !keys.includes(key)))
      return outcome.ok ? kept : { ...kept, ...outcome.errors }
    })
    // Keys taken from other actions change those too: the list comes back from the app.
    if (outcome.ok) await load()
    return outcome.ok
  }

  const saveKeys = (action: string, keys: string[]) => save({ [action]: keys.join(', ') })

  function stopWaiting() {
    setWait(null)
    setNotice(null)
  }

  // While a keycap waits, every key press is the page's: none reaches CarPlay, the shell, or the window's menu.
  useEffect(() => {
    if (!wait || !data) return
    const onKey = (event: KeyboardEvent) => {
      event.preventDefault()
      event.stopPropagation()
      const plain = !event.ctrlKey && !event.altKey && !event.shiftKey && !event.metaKey
      if (event.code === 'Escape' && plain) return stopWaiting()
      if (isModifier(event.code)) return
      const name = bindingName(event)
      if (!name) return setNotice("This key can't be used")
      const lower = name.toLowerCase()
      const owner = Object.entries(data.actions).find(
        ([key, action]) => key !== wait.action && action.fixed.some((fixed) => fixed.toLowerCase() === lower),
      )
      if (owner) return setNotice(FIXED_REASON[lower])
      const keys = [...data.actions[wait.action].keys]
      if (wait.index === null) keys.push(name)
      else keys[wait.index] = name
      // A key the action has already is not a second one.
      const unique = keys.filter(
        (key, index) => keys.findIndex((other) => other.toLowerCase() === key.toLowerCase()) === index,
      )
      stopWaiting()
      if (unique.join() !== data.actions[wait.action].keys.join()) void saveKeys(wait.action, unique)
    }
    window.addEventListener('keydown', onKey, true)
    return () => window.removeEventListener('keydown', onKey, true)
  }, [wait, data])

  if (!loaded) return null
  if (!data)
    return <Message tone="warn" head="No key bindings." text="This ChairPlay has no controls.properties to change." />

  const value = (key: string) => data.settings[key] ?? ''
  const changedActions = Object.entries(data.actions).filter(([, action]) => action.keys.join() !== action.defaults.join())
  const numberField = (key: string, label: string) => (
    <Field
      label={label}
      className="n3"
      value={value(key)}
      placeholder=""
      error={errors[key]}
      onCommit={(text) => save({ [key]: text })}
    />
  )

  function actionRow([key, label, title]: [string, string, string?]) {
    const action = data!.actions[key]
    if (!action) return null
    const waitingHere = wait?.action === key
    const waitCap = (
      <span key="wait" className="kbd wait">
        Press a key · Esc cancels
      </span>
    )
    const differs = action.keys.join() !== action.defaults.join()
    return (
      <Row key={key} label={label} title={title} className="keys-row">
        {action.keys.map((name, index) => {
          const fixed = action.fixed.some((key) => key.toLowerCase() === name.toLowerCase())
          if (fixed) {
            return (
              <span key={name} className="kbd lock" title={FIXED_REASON[name.toLowerCase()]}>
                {keyLabel(name)}
                <LockIcon />
              </span>
            )
          }
          if (waitingHere && wait.index === index) return waitCap
          return (
            <button
              key={name}
              type="button"
              className="kbd"
              title="Click to press another key; right-click to remove it"
              onClick={() => {
                setNotice(null)
                setWait({ action: key, index })
              }}
              onContextMenu={(event) => {
                event.preventDefault()
                void saveKeys(
                  key,
                  action.keys.filter((_, other) => other !== index),
                )
              }}
            >
              {keyLabel(name)}
            </button>
          )
        })}
        {waitingHere && wait.index === null && waitCap}
        {action.keys.length === 0 && !waitingHere && <span className="off">Off</span>}
        {action.keys.length < MOST_KEYS && !waitingHere && (
          <button
            type="button"
            className="add"
            aria-label="Add a key"
            title="Add a key"
            onClick={() => {
              setNotice(null)
              setWait({ action: key, index: null })
            }}
          >
            +
          </button>
        )}
        {differs && !waitingHere && (
          <button
            type="button"
            className="btn small reset"
            title={`Use the default keys: ${action.defaults.map(keyLabel).join(', ') || 'none'}`}
            onClick={() => void saveKeys(key, action.defaults)}
          >
            Reset
          </button>
        )}
        {waitingHere && notice && <span className="errtext">{notice}</span>}
        {errors[key] && <span className="errtext">{errors[key]}</span>}
      </Row>
    )
  }

  return (
    <div className="keyboard">
      <Group title="General" />
      <GlobalShortcuts data={data} state={state} save={save} error={errors['global.enabled']} />
      <TextInputSettings state={state} settings={settings} />
      <Row title="Hold Alt to see them for a moment">
        <Switch
          label="Show keys on the buttons"
          checked={settings.value('ui.controls.keys') === 'true'}
          onChange={(checked) => settings.save({ 'ui.controls.keys': String(checked) })}
        />
      </Row>
      <Group title="Mouse" />
      <Row label="Wheel">
        <Segmented
          label="Wheel"
          value={value('wheel.action')}
          options={[
            ['knob', 'Knob'],
            ['none', 'Off'],
          ]}
          onChange={(chosen) => void save({ 'wheel.action': chosen })}
        />
        <span className="gap" />
        <Switch
          label="Invert"
          checked={value('wheel.invert') === 'true'}
          onChange={(checked) => void save({ 'wheel.invert': String(checked) })}
        />
        {errors['wheel.action'] && <span className="errtext">{errors['wheel.action']}</span>}
      </Row>
      <Row label="Wheel step" title="Wheel notches for one knob step">
        {numberField('wheel.notches.per.step', 'Wheel step')}
        <span className="unit">notches</span>
      </Row>
      {/* A held key repeats some 30 times a second: these steps take every Nth repeat (owner, 2026-11-11). */}
      <Group title="Held keys" />
      <Row label="Turn" title="Holding Turn forward or Turn back, the knob turns one step every N key repeats; more is slower">
        {numberField('key.repeat.every', 'Turn')}
        <span className="unit">repeats per step</span>
      </Row>
      <Row
        label="AltScreen zoom"
        title="Holding a zoom key, the AltScreen map zooms one step every N key repeats; more is slower, and 0 zooms once a press"
      >
        {numberField('key.zoom.repeat.every', 'AltScreen zoom')}
        <span className="unit">repeats per step</span>
      </Row>
      {KEY_GROUPS.map(([group, actions]) => (
        <div key={group}>
          <Group title={group} />
          {actions.map(actionRow)}
        </div>
      ))}
      {/* Every action's keys back to the defaults in one save, as each row's Reset does for its own (owner). */}
      <Row>
        <button
          type="button"
          className="btn small"
          data-testid="keys-reset-all"
          disabled={changedActions.length === 0}
          title={changedActions.length === 0 ? 'All keys are the defaults' : 'Use the default keys for every action'}
          onClick={() =>
            void save(Object.fromEntries(changedActions.map(([key, action]) => [key, action.defaults.join(', ')])))
          }
        >
          Reset all keys
        </button>
      </Row>
    </div>
  )
}
