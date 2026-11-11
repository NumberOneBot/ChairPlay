// The settings pages' fields: text and password fields that save as they are left, and drop-down lists.
import { useRef, useState, type KeyboardEvent } from 'react'
import type { Choice } from './controls'
import { EyeIcon } from './icons'

/**
 * A text field that saves on Enter or when it loses the focus, and only what changed. [onCommit] resolving true says
 * the value was saved; otherwise what was typed stays, red with [error] beside it, rather than jumping back. Escape
 * puts the saved value back, and goes no further (it would otherwise show CarPlay).
 */
export function Field(props: {
  label: string
  value: string
  onCommit: (value: string) => unknown
  error?: string
  placeholder?: string
  className?: string
  type?: 'text' | 'password'
  title?: string
  /** False where the reason goes elsewhere, as under the safe-area frame: the field only turns red. */
  showReason?: boolean
  disabled?: boolean
  testId?: string
}) {
  const [draft, setDraft] = useState(props.value)
  const [dirty, setDirty] = useState(false)
  // A value saved, or a reason gone, by another control (a preset saving both sizes) replaces what was typed.
  const [seen, setSeen] = useState({ value: props.value, error: props.error })
  if (seen.value !== props.value || (seen.error && !props.error)) {
    setSeen({ value: props.value, error: props.error })
    setDirty(false)
  } else if (seen.error !== props.error) {
    setSeen({ value: props.value, error: props.error })
  }
  const shown = dirty ? draft : props.value

  function commit() {
    // While a reason shows, even the saved value is sent again: saving it is how the reason goes.
    if (shown === props.value && !props.error) return
    Promise.resolve(props.onCommit(shown)).then((saved) => {
      if (saved === true) setDirty(false)
    })
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') commit()
    if (event.key === 'Escape' && dirty) {
      event.preventDefault()
      setDirty(false)
    }
  }

  return (
    <>
      <input
        className={`t ${props.className ?? ''} ${props.error ? 'err' : ''}`}
        type={props.type ?? 'text'}
        aria-label={props.label}
        aria-invalid={props.error ? true : undefined}
        title={props.title}
        value={shown}
        placeholder={props.placeholder}
        disabled={props.disabled}
        data-testid={props.testId}
        autoComplete={props.type === 'password' ? 'new-password' : 'off'}
        spellCheck={false}
        onChange={(event) => {
          setDraft(event.target.value)
          setDirty(true)
        }}
        onKeyDown={onKeyDown}
        onBlur={commit}
      />
      {props.error && props.showReason !== false && <span className="errtext">{props.error}</span>}
    </>
  )
}

/**
 * A password field: empty, with dots for a saved password, and saves what is typed. The eye shows what is typed, and
 * the saved password itself, which it asks the app for ([saved]) only then; hiding it empties the field again.
 */
export function PasswordField(props: {
  label: string
  set: boolean
  onCommit: (value: string) => unknown
  error?: string
  saved?: () => Promise<string | null>
}) {
  const [reveal, setReveal] = useState(false)
  const [shown, setShown] = useState<string | null>(null)
  const visible = useRef(false)
  const reads = useRef(0)
  const saving = useRef<Promise<unknown> | null>(null)
  const commit = (text: string) => {
    const pending = Promise.resolve(props.onCommit(text)).then((saved) => {
      if (saved === true) {
        // A read started before this save must not put the previous password back.
        reads.current++
        setShown(visible.current ? text : null)
      }
      return saved
    }).finally(() => {
      if (saving.current === pending) saving.current = null
    })
    saving.current = pending
    return pending
  }
  const eye = reveal ? 'Hide the password' : 'Show the password'
  const toggle = async () => {
    const show = !visible.current
    visible.current = show
    const request = ++reads.current
    setReveal(show)
    if (!show) {
      setShown(null)
      return
    }
    // Clicking the eye blurs the field and starts a save first; wait for that replacement.
    if (saving.current && await saving.current !== true) return
    if (request !== reads.current) return
    if (props.set && props.saved) {
      const value = await props.saved()
      if (visible.current && request === reads.current) setShown(value)
    }
  }
  return (
    <>
      <span className="pw">
        <Field
          label={props.label}
          type={reveal ? 'text' : 'password'}
          // Red by class only: a reason passed to the field would make it send the empty saved value again.
          className={`w200 ${props.set ? 'set' : ''} ${props.error ? 'err' : ''}`}
          value={shown ?? ''}
          placeholder={props.set ? '••••••••••' : 'Not set'}
          onCommit={commit}
        />
        <button type="button" className="eye" aria-label={eye} title={eye} onClick={() => void toggle()}>
          <EyeIcon />
        </button>
      </span>
      {props.error && <span className="errtext">{props.error}</span>}
    </>
  )
}

/** A drop-down list: the value saved and the text shown for each choice; its wrapper draws the caret in the tint. */
export function Select(props: {
  label: string
  value: string
  options: readonly Choice[]
  onChange: (value: string) => void
  className?: string
}) {
  return (
    <span className="caret">
      <select
        className={`t ${props.className ?? ''}`}
        aria-label={props.label}
        value={props.value}
        onChange={(event) => props.onChange(event.target.value)}
      >
        {props.options.map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
    </span>
  )
}
