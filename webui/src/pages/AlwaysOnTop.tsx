import { Group, Row, Switch } from '../shell/controls'
import type { Settings } from '../shell/useSettings'

/** Applies immediately to the desktop window and persists independently for each screen. */
export function AlwaysOnTop({ settings, setting }: { settings: Settings; setting: string }) {
  return (
    <>
      <Group title="Window" />
      <Row>
        <Switch
          label="Always on top"
          checked={settings.value(setting) === 'true'}
          onChange={(checked) => settings.save({ [setting]: String(checked) })}
        />
        {settings.error(setting) && <span className="errtext">{settings.error(setting)}</span>}
      </Row>
    </>
  )
}
