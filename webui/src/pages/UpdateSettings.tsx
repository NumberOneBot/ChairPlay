import { useState } from 'react'
import type { Backend } from '../api'
import { Row, Switch } from '../shell/controls'
import type { Settings } from '../shell/useSettings'

export function UpdateSettings({ backend, settings }: { backend: Backend; settings: Settings }) {
  const [checking, setChecking] = useState(false)
  const [failed, setFailed] = useState(false)
  const key = 'updates.check.on.start'

  async function check() {
    setChecking(true)
    setFailed(false)
    try {
      setFailed(!(await backend.checkUpdates()))
    } catch {
      setFailed(true)
    } finally {
      setChecking(false)
    }
  }

  return <>
    <Row>
      <Switch label="Check for updates at startup" checked={settings.value(key) !== 'false'}
        disabled={!settings.data} onChange={(on) => void settings.save({ [key]: String(on) })} />
      {settings.error(key) && <span className="errtext">{settings.error(key)}</span>}
    </Row>
    <Row>
      <button type="button" className="btn small" title="Look for a newer version of ChairPlay now" disabled={checking}
        onClick={check}>
        {checking ? 'Checking…' : 'Check'}
      </button>
      {failed && <span className="errtext">Couldn't check for updates. Close any update dialog and try again.</span>}
    </Row>
  </>
}
