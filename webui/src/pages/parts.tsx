// What the settings pages build their rows from, bound to the shared settings: a field and a segmented choice that
// save their key, the note a group's header gives, and the safe-area cross.
import type { ReactNode } from 'react'
import type { AppState, Backend, PictureWindow, WindowSizes } from '../api'
import { Row, Segmented, type Choice } from '../shell/controls'
import { Field } from '../shell/fields'
import type { Settings } from '../shell/useSettings'

/** The settings changed but not yet in effect: the state's restartPending and reconnectPending. */
export interface Pending {
  restart: readonly string[]
  reconnect: readonly string[]
}

export const pendingOf = (state: AppState): Pending => ({
  restart: state.restartPending,
  reconnect: state.reconnectPending,
})

const NOTHING_PENDING: Pending = { restart: [], reconnect: [] }

/** Marks a setting that was changed and waits for a restart or the next connection: one of [keys] is [pending]. */
export function Changed({ keys, pending }: { keys: readonly string[]; pending: Pending }) {
  const waits = (list: readonly string[]) => keys.some((key) => list.includes(key))
  if (!waits(pending.restart) && !waits(pending.reconnect)) return null
  return (
    <span
      className="later"
      title={waits(pending.restart) ? 'Changed; applies after restart' : 'Changed; applies at the next connection'}
    >
      ⟳
    </span>
  )
}

/** A choice changed and waiting for a restart or a connection ([pending]) is marked beside it. */
export function parts(settings: Settings, pending: Pending = NOTHING_PENDING) {
  const { value, error, save } = settings
  return {
    /** A field for [key]; [placeholder] is what applies while it is blank. */
    field: (key: string, label: string, placeholder: string, className = 'n') => (
      <Field
        label={label}
        className={className}
        value={value(key)}
        placeholder={placeholder}
        error={error(key)}
        onCommit={(text) => save({ [key]: text })}
      />
    ),
    /**
     * A segmented choice for [key], [fallback] chosen while it is blank, as the app then takes it; its [unit] after it,
     * and the mark of a change waiting last, as a field's.
     */
    choice: (
      key: string,
      label: string,
      fallback: string,
      options: readonly Choice[],
      className?: string,
      unit?: string,
    ) => (
      <>
        <Segmented
          label={label}
          value={value(key) || fallback}
          options={options}
          className={className}
          onChange={(chosen) => save({ [key]: chosen })}
        />
        {unit && <span className="unit">{unit}</span>}
        <Changed keys={[key]} pending={pending} />
      </>
    ),
    /** The app's word on when [keys] take effect, for their group's header: the page says what the app says. */
    note: (keys: readonly string[]) => {
      const applies = new Set(keys.map((key) => settings.data?.applies[key]))
      if (applies.size !== 1) return undefined
      if (applies.has('restart')) return 'Applies after restart'
      if (applies.has('reconnect')) return 'Applies at the next connection'
      return undefined
    },
  }
}

/**
 * A window's size: presets ([width, height, cars that use it]) over its width and height fields, [prefix].width and
 * [prefix].height. Blank in the file means [fallback], which the app then takes. A preset saves both numbers; a size
 * being corrected picks none.
 */
export function SizeRows(props: {
  settings: Settings
  prefix: string
  presets: readonly (readonly [width: number, height: number])[]
  columns?: number
  fallback: { width: number; height: number }
  /** Beside the fields: the CarPlay page's Actual size. */
  after?: ReactNode
  /** To mark a changed size. */
  pending?: Pending
}) {
  const { settings, prefix, fallback } = props
  const { field } = parts(settings)
  const [widthKey, heightKey] = [`${prefix}.width`, `${prefix}.height`]
  const width = settings.value(widthKey) || String(fallback.width)
  const height = settings.value(heightKey) || String(fallback.height)
  const refused = settings.error(widthKey) || settings.error(heightKey)
  return (
    <>
      <Row label="Size">
        <Segmented
          label="Size"
          columns={props.columns}
          value={refused ? '' : `${width}x${height}`}
          options={props.presets.map(([w, h]) => [`${w}x${h}`, `${w}×${h}`])}
          onChange={(preset) => {
            const [presetWidth, presetHeight] = preset.split('x')
            settings.save({ [widthKey]: presetWidth, [heightKey]: presetHeight })
          }}
        />
      </Row>
      <Row>
        {field(widthKey, 'Width', String(fallback.width), 'short c')}
        <span className="x times">×</span>
        {field(heightKey, 'Height', String(fallback.height), 'short c')}
        <span className="unit">px</span>
        <Changed keys={[widthKey, heightKey]} pending={props.pending ?? NOTHING_PENDING} />
        {props.after}
      </Row>
    </>
  )
}

/**
 * Back to the picture in use: brings a window dragged to another size back to one screen pixel per picture pixel; not
 * there while it is. The window's smallest size never outgrows its picture, so it can always get there. A size chosen
 * on the page becomes the picture at the next connection, which its mark says; it is left as it is.
 */
export function ActualSize(props: { sizes: WindowSizes | null; window: PictureWindow; backend: Backend }) {
  const { sizes } = props
  if (!sizes) return null
  const resized = sizes.width !== sizes.pictureWidth || sizes.height !== sizes.pictureHeight
  if (!resized) return null
  return (
    <button
      type="button"
      className="btn small actual"
      title={`Resize the window to ${sizes.pictureWidth}×${sizes.pictureHeight}, one screen pixel per picture pixel`}
      onClick={() => void props.backend.actualSize(props.window)}
    >
      Actual size
    </button>
  )
}

const EDGES = [
  ['top', 'Top'],
  ['left', 'Left'],
  ['right', 'Right'],
  ['bottom', 'Bottom'],
] as const

/**
 * Four numbers round a little frame, one for each edge of [prefix] ("display.safe" or "altscreen.safe"), each centred
 * in its field. A refused one turns red, with its reason under the frame.
 */
export function SafeArea(props: {
  settings: Settings
  prefix: string
  label: string
  pending?: Pending
  after?: ReactNode
  /** The row's tooltip; off, it says what turns the fields on. */
  title?: string
  disabled?: boolean
}) {
  const { settings, prefix, label } = props
  const reason = EDGES.map(([edge]) => settings.error(`${prefix}.${edge}`)).find(Boolean)
  const [top, left, right, bottom] = EDGES.map(([edge, name]) => {
    const key = `${prefix}.${edge}`
    return (
      <Field
        key={key}
        label={name}
        title={name}
        className="short c"
        // A blank edge is no margin: it reads 0 as a saved 0 does, and stays blank in the file until it is changed.
        value={settings.value(key) || '0'}
        error={settings.error(key)}
        showReason={false}
        disabled={props.disabled}
        onCommit={(text) => settings.save({ [key]: text })}
      />
    )
  })
  return (
    <Row label={label} className="safe-row" title={props.title}>
      <span className="safe">
        <span />
        {top}
        <span />
        {left}
        <span className="frame" />
        {right}
        <span />
        {bottom}
        <span />
      </span>
      {props.after}
      <Changed keys={EDGES.map(([edge]) => `${prefix}.${edge}`)} pending={props.pending ?? NOTHING_PENDING} />
      {reason && <span className="errtext">{reason}</span>}
    </Row>
  )
}
