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

/** The actions by group, in the page's order: each setting, the name the page gives it, and its tooltip. */
const GROUPS: [group: string, actions: [key: string, label: string, title?: string][]][] = [
  [
    'CarPlay',
    [
      ['key.knob.left', 'Left', 'Tilt the knob left to move the focus left'],
      ['key.knob.right', 'Right', 'Tilt the knob right'],
      ['key.knob.up', 'Up', 'Tilt the knob up'],
      ['key.knob.down', 'Down', 'Tilt the knob down'],
      ['key.knob.next', 'Turn forward', 'Turn the knob one step forward'],
      ['key.knob.previous', 'Turn back', 'Turn the knob one step back'],
      ['key.select', 'Select', 'Press the knob'],
      ['key.back', 'Back'],
      ['key.home', 'Home', 'CarPlay home screen'],
      ['key.siri', 'Siri'],
    ],
  ],
  [
    'Media and calls',
    [
      ['key.media.previous', 'Previous track'],
      ['key.media.playpause', 'Play or pause'],
      ['key.media.next', 'Next track'],
      ['key.volume.up', 'Volume up', "ChairPlay's own volume; the Windows volume stays"],
      ['key.volume.down', 'Volume down'],
      ['key.volume.mute', 'Mute'],
      ['key.call.answer', 'Answer call'],
      ['key.call.end', 'End call'],
    ],
  ],
  [
    'AltScreen',
    [
      ['key.altscreen.content', 'HUD or map', 'Switch the AltScreen between the HUD and the map view picked last'],
      ['key.altscreen.cards', 'Move cards', 'Auto, left, right, top'],
      ['key.altscreen.zoom.in', 'Zoom in', 'Zoom the AltScreen map in'],
      ['key.altscreen.zoom.out', 'Zoom out', 'Zoom the AltScreen map out'],
    ],
  ],
  [
    'ChairPlay',
    [
      ['key.reconnect', 'Reconnect', 'End the CarPlay session gracefully and connect again'],
      ['key.screenshot', 'Screenshot', 'Save the CarPlay screen as a PNG file in the screenshots folder'],
      ['key.fullscreen', 'Full screen', 'Toggle full screen for the focused CarPlay or AltScreen window'],
      ['key.drive', 'Drive mode', 'Drive the GPS car with the arrow keys instead of moving through CarPlay, until pressed again'],
      ['key.gauges', 'Gauge size', "Switch the GPS car's gauges between large and small"],
    ],
  ],
]

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
      <Row label="Repeat every" title="A held key acts on every Nth repeat">
        {numberField('key.repeat.every', 'Repeat every')}
      </Row>
      <Row label="Zoom repeat every" title="A held AltScreen zoom key acts on every Nth repeat">
        {numberField('key.zoom.repeat.every', 'Zoom repeat every')}
      </Row>
      {GROUPS.map(([group, actions]) => (
        <div key={group}>
          <Group title={group} />
          {actions.map(actionRow)}
        </div>
      ))}
    </div>
  )
}
