import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { AppState, Backend } from './api'
import { icons } from './controlsIcons'
import { useKeysToApp } from './shell/useKeysToApp'

/** Key names as Windows writes them: controls.properties says Escape, Equal and KP_Add for Esc, = and Num +. */
const KEY_LABELS: Record<string, string> = {
  escape: 'Esc',
  equal: '=',
  minus: '−',
  kp_add: 'Num +',
  kp_subtract: 'Num −',
  kp_enter: 'Num Enter',
  kp_multiply: 'Num *',
  kp_divide: 'Num /',
  kp_decimal: 'Num .',
  pageup: 'Page Up',
  pagedown: 'Page Down',
  backspace: 'Backspace',
  capslock: 'Caps Lock',
  printscreen: 'Print Screen',
  ctrl: 'Ctrl',
  shift: 'Shift',
  alt: 'Alt',
  scrolllock: 'Scroll Lock',
  numlock: 'Num Lock',
  leftbracket: '[',
  rightbracket: ']',
  backslash: '\\',
  semicolon: ';',
  apostrophe: "'",
  comma: ',',
  period: '.',
  slash: '/',
  graveaccent: '`',
  kp_equal: 'Num =',
  mediaprevious: 'Previous track',
  mediaplaypause: 'Play/Pause',
  medianext: 'Next track',
}

export function keyLabel(name: string): string {
  return name
    .split('+')
    .map((part) => {
      const trimmed = part.trim()
      const lower = trimmed.toLowerCase()
      if (KEY_LABELS[lower]) return KEY_LABELS[lower]
      if (/^kp_\d$/.test(lower)) return `Num ${lower.slice(3)}`
      return trimmed.charAt(0).toUpperCase() + trimmed.slice(1)
    })
    .join('+')
}

interface Shared {
  backend: Backend
  keys: Record<string, string | null>
  showKeys: boolean
}

/**
 * A button that holds its control while the pointer holds it, as a key does: down on the press, up on the release,
 * wherever the pointer goes meanwhile. Its key shows while Alt is held.
 */
function Key(
  props: Shared & {
    action: string
    label: string
    icon: ReactNode
    /** Shown under the icon; none for the media and volume keys, whose icons say it. */
    text?: string
    className?: string
    disabled?: boolean
    /** The tooltip while disabled: what enables it. */
    disabledTitle?: string
    pressed?: boolean
  },
) {
  const held = useRef(false)
  const [down, setDown] = useState(false)
  const key = props.keys[props.action]
  const release = () => {
    if (!held.current) return
    held.current = false
    setDown(false)
    void props.backend.control(props.action, false)
  }
  return (
    <button
      type="button"
      className={`key ${props.className ?? ''} ${down ? 'down' : ''}`}
      aria-label={props.label}
      aria-pressed={props.pressed}
      title={
        props.disabled && props.disabledTitle
          ? props.disabledTitle
          : key
            ? `${props.label} (${keyLabel(key)})`
            : props.label
      }
      disabled={props.disabled}
      onPointerDown={(event) => {
        if (props.disabled || held.current) return
        held.current = true
        setDown(true)
        event.currentTarget.setPointerCapture?.(event.pointerId)
        void props.backend.control(props.action, true)
      }}
      onPointerUp={release}
      onPointerCancel={release}
      onLostPointerCapture={release}
    >
      {props.icon}
      {props.text && <span>{props.text}</span>}
      {/* Over the button in its top-right corner: nothing on it moves as the keys show */}
      {props.showKeys && key && <small className="kb">{keyLabel(key)}</small>}
    </button>
  )
}

/**
 * The knob, a puck: Select in the middle, each tilt a quarter of the pad round it; wings over its corners, the
 * AltScreen's zoom over the top ones, which only an AltScreen on screen has, and the turns under the bottom ones; the
 * wheel over it turns it.
 */
function Knob(props: Shared & { onTurn: (forward: boolean) => void; zoom: boolean }) {
  const part = (action: string, label: string, icon: ReactNode, place: string, disabled = false) => (
    <Key
      {...props}
      action={action}
      label={label}
      icon={icon}
      className={place}
      disabled={disabled}
      disabledTitle="Show Map or Map + card on the AltScreen to zoom its map"
    />
  )
  return (
    <div
      className="knob"
      role="group"
      aria-label="Knob"
      onWheel={(event) => {
        if (event.deltaY !== 0) props.onTurn(event.deltaY > 0)
      }}
    >
      <span className="ring" />
      {part('altscreen.zoom.out', 'Zoom out', icons.zoomOut, 'wing wing-top-left', !props.zoom)}
      {part('altscreen.zoom.in', 'Zoom in', icons.zoomIn, 'wing wing-top-right', !props.zoom)}
      {part('knob.previous', 'Turn back', icons.turnBack, 'wing wing-bottom-left turn-back')}
      {part('knob.next', 'Turn forward', icons.turnForward, 'wing wing-bottom-right turn-forward')}
      {part('knob.up', 'Tilt up', icons.up, 'tilt tilt-up')}
      {part('knob.down', 'Tilt down', icons.down, 'tilt tilt-down')}
      {part('knob.left', 'Tilt left', icons.left, 'tilt tilt-left')}
      {part('knob.right', 'Tilt right', icons.right, 'tilt tilt-right')}
      <Key {...props} action="select" label="Select" icon={null} text="Select" className="select" />
    </div>
  )
}

/**
 * The controls window (207x416): the knob with its zoom and turns on top, the CarPlay, media, volume and call keys
 * under it. A button press reaches the app at once; the window keeps the keyboard, so the keys work as in the main
 * window, and Alt held there or here shows each button its key.
 */
export function ControlsScreen({ state, backend }: { state: AppState; backend: Backend }) {
  const controls = state.controls
  const [altHere, setAltHere] = useState(false)
  // A click on a button gives the page the keyboard: every key of the app works over it as over the window.
  useKeysToApp(backend, true)

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Alt') setAltHere(event.type === 'keydown')
    }
    const onBlur = () => setAltHere(false)
    window.addEventListener('keydown', onKey)
    window.addEventListener('keyup', onKey)
    window.addEventListener('blur', onBlur)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('keyup', onKey)
      window.removeEventListener('blur', onBlur)
    }
  }, [])

  const shared: Shared = {
    backend,
    keys: controls?.keys ?? {},
    showKeys: Boolean(controls?.altHeld || altHere || state.controlsKeys),
  }
  // Only an AltScreen on screen showing a map, Map or Map + card, has a map to zoom.
  const view = controls?.altscreen ?? null
  const altOn =
    (view === 'map' || view === 'map-card') &&
    state.windows.altscreen.available &&
    state.windows.altscreen.visible &&
    state.canShowCarPlay
  const turn = (forward: boolean) => {
    const action = forward ? 'knob.next' : 'knob.previous'
    void backend.control(action, true)
    void backend.control(action, false)
  }

  return (
    <div className="controls-screen">
      <Knob {...shared} onTurn={turn} zoom={altOn} />
      <div className="key-rows">
        <div className="key-row three">
          <Key {...shared} action="back" label="Back" icon={icons.back} text="Back" />
          <Key {...shared} action="home" label="Home" icon={icons.home} text="Home" />
          <Key {...shared} action="siri" label="Siri" icon={icons.siri} text="Siri" />
        </div>
        <div className="key-row three">
          <Key {...shared} action="media.previous" label="Previous track" icon={icons.previous} />
          <Key
            {...shared}
            action="media.playpause"
            label="Play or pause"
            icon={controls?.playing ? icons.pause : icons.play}
          />
          <Key {...shared} action="media.next" label="Next track" icon={icons.next} />
        </div>
        <div className="key-row three">
          <Key {...shared} action="volume.down" label="Volume down" icon={icons.volumeDown} />
          <Key
            {...shared}
            action="volume.mute"
            label="Mute"
            icon={icons.mute}
            pressed={controls?.volume.muted ?? false}
          />
          <Key {...shared} action="volume.up" label="Volume up" icon={icons.volumeUp} />
        </div>
        <div className="key-row two">
          <Key {...shared} action="call.answer" label="Answer" icon={icons.answer} text="Answer" className="answer" />
          <Key {...shared} action="call.end" label="End" icon={icons.end} text="End" className="end" />
        </div>
      </div>
    </div>
  )
}
