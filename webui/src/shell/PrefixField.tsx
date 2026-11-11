import { Field } from './fields'

/**
 * A field with a fixed start: one box, [prefix] in gray and not editable, then the field ([Field]) right after it,
 * which alone is the value and saves as a Field does. The box takes the focus ring and the red of a refused value; a
 * click on the prefix puts the focus in the field (the box is its label). The reason shows under the box.
 */
export function PrefixField(props: {
  prefix: string
  label: string
  value: string
  onCommit: (value: string) => unknown
  error?: string
  placeholder?: string
  testId?: string
}) {
  return (
    <>
      <label className="pre">
        <span>{props.prefix}</span>
        <Field
          label={props.label}
          value={props.value}
          placeholder={props.placeholder}
          error={props.error}
          showReason={false}
          onCommit={props.onCommit}
          testId={props.testId}
        />
      </label>
      {props.error && <span className="errtext">{props.error}</span>}
    </>
  )
}
