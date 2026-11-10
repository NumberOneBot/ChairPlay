// The settings pages' parts, as desktop-docs/mockups/desktop-ui.html draws them: a property grid of rows under group
// headers, segmented groups, checkboxes, and the messages that say what is wrong; the fields are in fields.tsx.
import { useState, type CSSProperties, type ReactNode } from 'react'

/** A group's header: its name in bold capitals, and a note at its right end. */
export function Group({ title, note }: { title: string; note?: string }) {
  return (
    <h3 className="grp">
      {title}
      {note && <span>{note}</span>}
    </h3>
  )
}

/** One line of the property grid: the label right-aligned in its column, the controls beside it. */
export function Row(props: {
  label?: string
  htmlFor?: string
  title?: string
  className?: string
  children: ReactNode
}) {
  return (
    <div className={`row ${props.className ?? ''}`} title={props.title}>
      <label htmlFor={props.htmlFor}>{props.label}</label>
      <div className="ctl">{props.children}</div>
    </div>
  )
}

/** One choice of a segmented group: the value saved, the text shown, and its tooltip. */
/** A choice's value, its text, its tooltip, and a picture before the text. */
export type Choice = readonly [value: string, text: string, title?: string, icon?: ReactNode]

/**
 * Choices side by side, the chosen one a lit plate. A disabled group shows none chosen. Two or three choices are equal
 * plates; four or more ("wide") keep to the length of a row's controls, and are as long as four plates where the page
 * has room, and five where it has room for that; six or more ("rows") wrap onto two rows of ordinary plates, as long
 * as a group of as many as a row holds (three for six), or [columns] a row. [className]
 * sets the width by hand instead, for a group no other is like: "half" for short figures, narrow plates two of which
 * are as long as a size's two fields, or a grid of its own ("rows view-areas", "rows cities").
 */
export function Segmented(props: {
  label: string
  value: string
  options: readonly Choice[]
  onChange: (value: string) => void
  disabled?: boolean
  className?: string
  columns?: number
}) {
  const columns = props.columns ?? Math.ceil(props.options.length / 2)
  return (
    <span
      className={`seg ${props.className ?? (props.options.length >= 6 ? 'rows' : props.options.length >= 4 ? 'wide' : '')}`}
      data-items={props.options.length}
      data-columns={columns}
      style={props.options.length >= 6 ? ({ '--columns': columns } as CSSProperties) : undefined}
      role="radiogroup"
      aria-label={props.label}
    >
      {props.options.map(([value, text, title, icon]) => {
        const chosen = !props.disabled && value === props.value
        return (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={chosen}
            className={chosen ? 'on' : ''}
            title={title}
            disabled={props.disabled}
            onClick={() => {
              if (!chosen) props.onChange(value)
            }}
          >
            {icon}
            {text}
          </button>
        )
      })}
    </span>
  )
}

/**
 * A level from [min] to [max], 0 to 100 unless given: a track like a segmented group's frame, lit up to the level as a
 * chosen plate is, under [marks] marks, with a plate to drag, and the level beside it, in percent unless [format] says
 * otherwise ([idle] in its place while it is not dragged, for a muted level). Each move passes the level on; while it
 * is dragged the slider keeps its own, so the app's answer does not pull it back, and follows [value] again once let go.
 */
export function Slider(props: {
  label: string
  value: number
  onChange: (value: number) => void
  idle?: string
  step?: number
  min?: number
  max?: number
  marks?: number
  format?: (value: number) => string
}) {
  const [dragged, setDragged] = useState<number | null>(null)
  const value = dragged ?? props.value
  const letGo = () => setDragged(null)
  const idle = dragged === null ? props.idle : undefined
  const min = props.min ?? 0
  const max = props.max ?? 100
  return (
    <>
      <input
        type="range"
        className="slider"
        aria-label={props.label}
        min={min}
        max={max}
        step={props.step ?? 1}
        value={value}
        style={{ '--level': `${((value - min) / (max - min)) * 100}%`, '--marks': props.marks ?? 20 } as CSSProperties}
        onChange={(event) => {
          const level = Number(event.target.value)
          setDragged(level)
          props.onChange(level)
        }}
        onPointerUp={letGo}
        onKeyUp={letGo}
        onBlur={letGo}
      />
      <span className={`level ${idle ? 'idle' : ''}`}>{idle ?? props.format?.(value) ?? `${value}%`}</span>
    </>
  )
}

/** A setting turned on or off at once: a checkbox drawn and announced as a switch */
export function Switch(props: {
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
}) {
  return (
    <label className="chk">
      <input
        type="checkbox"
        role="switch"
        checked={props.checked}
        disabled={props.disabled}
        onChange={(event) => props.onChange(event.target.checked)}
      />
      {props.label}
    </label>
  )
}

/**
 * What is wrong: the problem in the message's colour, then the cause and what to do, the time at the right for an
 * error; [children] go on the grid below, a field in the wide column and its button beside it, or a line of actions.
 */
export function Message(props: {
  tone: 'bad' | 'warn' | 'info'
  head: string
  text: ReactNode
  at?: string
  title?: string
  children?: ReactNode
}) {
  return (
    <div className={`alert ${props.tone}`} role={props.tone === 'info' ? 'status' : 'alert'} title={props.title}>
      <div className="head">
        {props.head} <span>{props.text}</span>
      </div>
      <span className="when">{props.at}</span>
      {props.children}
    </div>
  )
}
