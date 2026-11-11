import { cleanup, fireEvent, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import type { AppState } from '../api'
import { outputs, tracks } from '../gpsFixtures'
import type { GpsOutput } from '../gpsApi'
import { absent, control } from '../testing'
import { gpsState, openSection } from './gpsTesting'
import type { TrackState } from './trackApi'

afterEach(cleanup)

/** The app's state with [output] and the recording [track] in its GPS. */
function withTrack(output: GpsOutput, track: TrackState | null = null): AppState {
  const state = gpsState(output)
  return { ...state, gps: { ...state.gps!, track } }
}

const inert = (element: HTMLElement) => element.closest('[inert]') !== null
const button = (testId: string) => control(testId, 'button') as HTMLButtonElement

describe('Track playback, where it shows', () => {
  it('offers Open… alone while no recording is loaded, and asks the app for the Open dialog', async () => {
    const { backend } = await openSection(withTrack(outputs.usbOn))
    expect(absent('track-play')).toBe(true)
    expect(absent('track-stop')).toBe(true)

    fireEvent.click(button('track-open'))
    expect(backend.trackActions).toEqual(['open'])
  })

  it.each([
    ['CarPlay GPS', outputs.carplayOn],
    ['USB not set up', outputs.usbUnprepared],
    ['USB set up, no cable', outputs.usbPreparedNoCable],
    ['USB with no position', outputs.usbNoPosition],
  ])('is not on the page with %s', async (_, output) => {
    await openSection(withTrack(output, tracks.stopped))
    expect(absent('track-section')).toBe(true)
  })

  it('waits whole for signal simulation while it is off, and sends nothing', async () => {
    const { backend } = await openSection(withTrack(outputs.usbOff, tracks.stopped))
    expect(inert(control('track-section', 'region'))).toBe(true)

    fireEvent.click(button('track-open'))
    fireEvent.click(button('track-play'))
    expect(backend.trackActions).toEqual([])
  })
})

describe('Track playback, the keys', () => {
  it('plays a stopped recording and pauses a playing one with the same key', async () => {
    const { backend, update } = await openSection(withTrack(outputs.usbOn, tracks.stopped))
    fireEvent.click(button('track-play'))
    update(withTrack(outputs.usbOn, tracks.playing))
    fireEvent.click(button('track-play'))

    expect(backend.trackActions).toEqual(['play', 'pause'])
  })

  it('holds Stop only while stopped at the start', async () => {
    const { update } = await openSection(withTrack(outputs.usbOn, tracks.stopped))
    expect(button('track-stop').disabled).toBe(true)

    for (const track of [tracks.stoppedAway, tracks.playing, tracks.paused]) {
      update(withTrack(outputs.usbOn, track))
      expect(button('track-stop').disabled).toBe(false)
    }
  })

  it('sends Stop', async () => {
    const { backend } = await openSection(withTrack(outputs.usbOn, tracks.paused))
    fireEvent.click(button('track-stop'))
    expect(backend.trackActions).toEqual(['stop'])
  })
})

describe('Track playback, the car', () => {
  it.each([
    ['playing', tracks.playing],
    ['paused', tracks.paused],
  ])('holds Position and Drive mode while the recording is %s, and no other car setting', async (_, track) => {
    await openSection(withTrack(outputs.usbOn, track))
    expect(inert(screen.getByTestId('gps-position-hold'))).toBe(true)
    expect(inert(screen.getByTestId('drive-mode-hold'))).toBe(true)
    expect(inert(control('gps-gauges-large', 'radio'))).toBe(false)
    expect(inert(control('gps-gauges-small', 'radio'))).toBe(false)
  })

  it('leaves a held Virtual car to its own hold, not the track’s', async () => {
    await openSection(withTrack(outputs.usbOff, tracks.paused))
    const drive = screen.getByTestId('drive-mode-hold')
    expect(drive.getAttribute('inert')).toBeNull()
    expect(inert(drive)).toBe(true)
  })

  it('holds nothing once the section is gone, as with CarPlay GPS', async () => {
    await openSection(withTrack(outputs.carplayOn, tracks.paused))
    expect(inert(screen.getByTestId('gps-position-hold'))).toBe(false)
    expect(inert(screen.getByTestId('drive-mode-hold'))).toBe(false)
  })

  it('gives the car back once stopped', async () => {
    await openSection(withTrack(outputs.usbOn, tracks.stoppedAway))
    expect(inert(screen.getByTestId('gps-position-hold'))).toBe(false)
    expect(inert(screen.getByTestId('drive-mode-hold'))).toBe(false)
  })
})

describe('Track playback, a file it cannot play', () => {
  it('says so inside the section, and not at the top of the page', async () => {
    await openSection(withTrack(outputs.usbTrackUnreadable, tracks.stopped))
    const notice = control('gps-notice-track-unreadable', 'alert')
    expect(control('track-section', 'region').contains(notice)).toBe(true)
    expect(screen.getAllByTestId('gps-notice-track-unreadable')).toHaveLength(1)
  })
})
