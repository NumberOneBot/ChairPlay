import type { AppState, Backend } from '../api'
import { Group, Row, Slider, Switch, type Choice } from '../shell/controls'
import { Select } from '../shell/fields'
import type { Settings } from '../shell/useSettings'
import { Changed, parts, pendingOf } from './parts'

/**
 * A device list: the Windows default first, then the devices Java Sound lists. A saved device the list lacks (one
 * unplugged, or a part of a name written in the file) stays as chosen, marked as not found.
 */
function deviceChoices(devices: readonly string[], saved: string): Choice[] {
  const choices: Choice[] = [['default', 'Windows default'], ...devices.map((name): Choice => [name, name])]
  if (saved && saved !== 'default' && !devices.includes(saved)) choices.push([saved, `${saved} (not found)`])
  return choices
}

/**
 * How loud CarPlay plays, where its sound goes, where the microphone for calls and Siri comes from, how the music fades
 * in and out, and how low navigation prompts put it.
 */
export function AudioPage({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const { value, save, data } = settings
  const pending = pendingOf(state)
  const { field, choice, note } = parts(settings, pending)
  const buffered = value('audio.music.mode') === 'buffered'
  const devices = data?.audio ?? { outputs: [], inputs: [] }
  const device = (key: string, label: string, names: readonly string[]) => (
    <Row label={label}>
      <Select
        label={label}
        className="w280"
        value={value(key) || 'default'}
        options={deviceChoices(names, value(key))}
        onChange={(chosen) => save({ [key]: chosen })}
      />
      <Changed keys={[key]} pending={pending} />
      {settings.error(key) && <span className="errtext">{settings.error(key)}</span>}
    </Row>
  )
  const volume = state.controls?.volume
  return (
    <>
      <Group title="Devices" note={note(['audio.output', 'audio.input', 'audio.echo.cancellation'])} />
      {device('audio.output', 'Output', devices.outputs)}
      {volume && (
        <Row
          label="Volume"
          title="ChairPlay's own volume, as the volume keys and the controls window set it; Windows' own stays as it is"
        >
          <Slider
            label="Volume"
            value={volume.level}
            idle={volume.muted ? 'Muted' : undefined}
            onChange={(level) => backend.volume(level)}
          />
        </Row>
      )}
      {device('audio.input', 'Microphone', devices.inputs)}
      <Row>
        <Switch
          label="Echo cancellation"
          checked={value('audio.echo.cancellation') === 'true'}
          onChange={(checked) => save({ 'audio.echo.cancellation': String(checked) })}
        />
        <span className="exp">Experimental</span>
        <Changed keys={['audio.echo.cancellation']} pending={pending} />
      </Row>
      <Row className="hint-row">
        <span className="hint">For calls through speakers rather than headphones</span>
      </Row>
      <Group title="Music" note="Mode applies at the next connection" />
      <Row label="Playback mode" title="Buffered playback lets supported iPhone apps send music ahead to cover Wi-Fi gaps">
        {choice('audio.music.mode', 'Playback mode', 'standard', [
          ['standard', 'Standard'],
          ['buffered', 'Buffered'],
        ])}
        {settings.error('audio.music.mode') && <span className="errtext">{settings.error('audio.music.mode')}</span>}
      </Row>
      {buffered || state.bufferedAudio ? (
        <Row label="Buffer" title="Unplayed audio in the receiver and output queue">
          <span role="status" aria-label="Buffered playback" aria-live="off">
            {state.bufferedAudio
              ? { loading: 'Loading', playing: 'Playing', paused: 'Paused', empty: 'Empty' }[state.bufferedAudio.state]
              : 'No stream'}
            {' · '}{(state.bufferedAudio?.seconds ?? 0).toFixed(1)} s
            {' · '}{((state.bufferedAudio?.bytes ?? 0) / 1024).toFixed(1)} KB
          </span>
        </Row>
      ) : <Row
        label="Startup silence"
        title="How long each new standard music stream stays silent before the volume rises"
      >
        {field('audio.start.silence.ms', 'Startup silence', '2000')}
        <span className="unit">ms</span>
      </Row>}
      <Row
        label="Volume fade"
        title="How long standard music streams take to rise after the pause, and all streams take to fade out as CarPlay disconnects or ChairPlay closes"
      >
        {field('audio.start.fade.ms', 'Volume fade', '800')}
        <span className="unit">ms</span>
      </Row>
      <Group title="Navigation prompts" note="From the next prompt" />
      <Row
        label="Music at"
        title="How loud the music stays under a prompt: -14 dB, Apple's own, is a fifth of its volume"
      >
        <Slider
          label="Music at"
          min={-30}
          max={0}
          marks={15}
          value={Number(value('audio.duck.db') || '-14')}
          format={(level) => `${level < 0 ? '−' : ''}${Math.abs(level)} dB`}
          onChange={(level) => save({ 'audio.duck.db': String(level) })}
        />
        <Changed keys={['audio.duck.db']} pending={pending} />
      </Row>
      <Row title="Use the level the iPhone sends with a prompt; Music at applies when it sends none">
        <Switch
          label="Use iPhone ducking level"
          checked={value('audio.duck.iphone') !== 'false'}
          onChange={(checked) => save({ 'audio.duck.iphone': String(checked) })}
        />
        <Changed keys={['audio.duck.iphone']} pending={pending} />
      </Row>
    </>
  )
}
