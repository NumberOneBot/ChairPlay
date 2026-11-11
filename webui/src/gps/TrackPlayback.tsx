import type { Backend } from '../api'
import { icons } from '../controlsIcons'
import type { GpsOutput } from '../gpsApi'
import { Group, Row } from '../shell/controls'
import { NoticeBlock, useLiveNotices } from './GpsNotices'
import { StopIcon, TrackIcon } from './icons'
import type { TrackAction, TrackState } from './trackApi'
import { stopOff, trackHeld } from './trackRules'

const two = (value: number) => String(Math.floor(value)).padStart(2, '0')

/** [seconds] as m:ss, or h:mm:ss when [hours]. */
function clock(seconds: number, hours: boolean): string {
  const whole = Math.max(0, Math.floor(seconds))
  return hours
    ? `${Math.floor(whole / 3600)}:${two((whole % 3600) / 60)}:${two(whole % 60)}`
    : `${Math.floor(whole / 60)}:${two(whole % 60)}`
}

/** The line under the file's name: where the player is, the recording's length, and its distance when known. */
function recordingLine(track: TrackState): string {
  const hours = track.duration >= 3600
  const distance = track.distanceKm === undefined ? null : `${track.distanceKm.toFixed(2)} km`
  return [`${clock(track.time, hours)} / ${clock(track.duration, hours)}`, distance].filter(Boolean).join(' · ')
}

/**
 * Track playback, between Position and Virtual car on the GPS page (desktop-docs/gps-track-playback-ui-spec.md): Open…
 * asks the app for the Windows Open dialog, the recording it loaded and where the player is come back as state, and
 * the keys play, pause, and stop it. Shown for a ready USB GPS only (trackShown); while simulation is off it waits whole.
 * A file it could not play is said here, under the header, rather than at the top of the page.
 */
export function TrackPlayback({
  output,
  track,
  backend,
}: {
  output: GpsOutput
  track: TrackState | null
  backend: Backend
}) {
  const held = trackHeld(output)
  const notices = useLiveNotices(output.notices).filter(({ code }) => code === 'track-unreadable')
  const send = (action: TrackAction) => void backend.track(action)
  const playing = track?.phase === 'playing'
  const playTitle = held ?? (playing ? 'Pause the recording' : 'Play the recording')
  return (
    <section
      aria-label="Track playback"
      data-testid="track-section"
      className={held ? 'track-playback held' : 'track-playback'}
      inert={held !== null}
      title={held ?? undefined}
    >
      <Group title="Track playback" note="GPX" />
      {notices.map((notice) => (
        <NoticeBlock key={notice.id} notice={notice} />
      ))}
      <Row className="track-open-row">
        <button
          type="button"
          className="btn small"
          title="Open a recording"
          data-testid="track-open"
          disabled={held !== null}
          onClick={() => send('open')}
        >
          <TrackIcon />
          Open…
        </button>
      </Row>
      <Row label="Recording" className="track-file-row">
        <div className="track-file">
          <span className="track-file-name">{track ? track.name : 'No recording selected'}</span>
          {track && <output className="track-meta track-time">{recordingLine(track)}</output>}
        </div>
      </Row>
      {track && (
        <Row label="Playback" className="track-keys-row">
          <span className="track-keys" role="group" aria-label="Playback controls">
            <button
              type="button"
              className="key lead"
              title={playTitle}
              aria-label={playTitle}
              data-testid="track-play"
              disabled={held !== null}
              onClick={() => send(playing ? 'pause' : 'play')}
            >
              {playing ? icons.pause : icons.play}
            </button>
            <button
              type="button"
              className="key"
              title="Stop and go back to the start"
              aria-label="Stop and go back to the start"
              data-testid="track-stop"
              disabled={held !== null || stopOff(track)}
              onClick={() => send('stop')}
            >
              <StopIcon />
            </button>
          </span>
        </Row>
      )}
    </section>
  )
}
