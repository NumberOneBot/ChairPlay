import type { AppState, ControlsData } from '../api'
import { Row, Switch } from '../shell/controls'

export function GlobalShortcuts({ data, state, save, error }: {
  data: ControlsData
  state: AppState
  save: (changes: Record<string, string>) => Promise<boolean>
  error?: string
}) {
  const problem = error || state.controls?.globalKeyboard?.error
  return (
    <>
      <Row>
        <Switch label="Global shortcuts" checked={data.settings['global.enabled'] === 'true'}
          onChange={(enabled) => void save({ 'global.enabled': String(enabled) })} />
      </Row>
      <Row className="hint-row">
        <span className="hint">Use these shortcuts while another app has focus; that app also receives the keys</span>
      </Row>
      {problem && <Row><span className="errtext" role="alert">{problem}</span></Row>}
    </>
  )
}
