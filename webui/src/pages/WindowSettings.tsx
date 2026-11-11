import { Group, Row, Switch } from '../shell/controls'
import type { Settings } from '../shell/useSettings'

/**
 * The Application page's Windows group: each window's own switches, a row each under the window's name. They apply at
 * once to the desktop window and change nothing of the iPhone's session.
 */
export function WindowSettings({ settings }: { settings: Settings }) {
  const toggle = (key: string, label: string, testId: string) => (
    <>
      <Switch
        label={label}
        checked={settings.value(key) === 'true'}
        onChange={(checked) => settings.save({ [key]: String(checked) })}
        testId={testId}
      />
      {settings.error(key) && <span className="errtext">{settings.error(key)}</span>}
    </>
  )
  return (
    <>
      <Group title="Windows" />
      <Row label="CarPlay">{toggle('window.always.on.top', 'Always on top', 'app-carplay-on-top')}</Row>
      <Row title="Show the CarPlay window and bring it to the front when a call comes in. Its taskbar button flashes on a call either way.">
        {toggle('window.front.on.call', 'Bring to front on a call', 'app-carplay-front-on-call')}
      </Row>
      <Row label="AltScreen">{toggle('altscreen.always.on.top', 'Always on top', 'app-altscreen-on-top')}</Row>
    </>
  )
}
