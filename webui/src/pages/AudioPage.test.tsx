import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

/** The shell on the Audio page, once the settings are in. */
async function audio(backend = new FakeBackend()) {
  render(<Shell state={appState({ phase: 'active', canShowCarPlay: true })} backend={backend} />)
  await screen.findByRole('textbox', { name: 'Network' })
  fireEvent.click(screen.getByRole('tab', { name: 'Audio' }))
  return backend
}

const select = (name: string) => screen.getByRole('combobox', { name }) as HTMLSelectElement

describe('Audio page volume', () => {
  // The app's own volume, as the volume keys and the controls window set it; Windows' stays as it is.
  it('shows the volume and sets it with the slider', async () => {
    const backend = await audio()

    const slider = screen.getByRole('slider', { name: 'Volume' }) as HTMLInputElement
    expect(slider.value).toBe('60')
    fireEvent.change(slider, { target: { value: '37' } })

    expect(backend.volumes).toEqual([37])
  })

  // The music starts silent while the iPhone's first bursts buffer, then rises; it fades out over the same time as
  // CarPlay disconnects or the app closes.
  it('sets how the music fades in and out', async () => {
    const backend = await audio()

    const silence = screen.getByRole('textbox', { name: 'Startup silence' }) as HTMLInputElement
    fireEvent.change(silence, { target: { value: '1500' } })
    fireEvent.blur(silence)
    const fade = screen.getByRole('textbox', { name: 'Volume fade' }) as HTMLInputElement
    fireEvent.change(fade, { target: { value: '500' } })
    fireEvent.blur(fade)

    await waitFor(() =>
      expect(backend.saved).toEqual([{ 'audio.start.silence.ms': '1500' }, { 'audio.start.fade.ms': '500' }]),
    )
  })

  // Navigation prompts lower the music to the iPhone's level, as a car's head unit does, or to the level set when it
  // sends none or is not let.
  it('sets how low prompts put the music', async () => {
    const backend = await audio()

    // In decibels, as the iPhone sends its own, from -30 dB to 0 in steps of 1, at Apple's -14 dB unless set.
    const level = screen.getByRole('slider', { name: 'Music at' }) as HTMLInputElement
    expect([level.min, level.max, level.step, level.value]).toEqual(['-30', '0', '1', '-14'])
    fireEvent.change(level, { target: { value: '-20' } })
    const iPhone = screen.getByRole('switch', { name: 'Use iPhone ducking level' }) as HTMLInputElement
    expect(iPhone.checked).toBe(true)
    fireEvent.click(iPhone)

    await waitFor(() => expect(backend.saved).toEqual([{ 'audio.duck.db': '-20' }, { 'audio.duck.iphone': 'false' }]))
  })
})

describe('Audio page', () => {
  it('replaces startup silence only while a buffered stream exists and restores it after teardown', async () => {
    const backend = new FakeBackend()
    const state = appState({ phase: 'active', canShowCarPlay: true })
    const view = render(<Shell state={state} backend={backend} />)
    await screen.findByRole('textbox', { name: 'Network' })
    fireEvent.click(screen.getByRole('tab', { name: 'Audio' }))
    expect(screen.queryByRole('status', { name: 'Buffered playback' })).toBeNull()
    view.rerender(<Shell state={{ ...state, bufferedAudio: { state: 'playing', seconds: 12.3, bytes: 345600 } }} backend={backend} />)
    expect(screen.queryByRole('textbox', { name: 'Startup silence' })).toBeNull()
    expect(screen.getByRole('status', { name: 'Buffered playback' })).not.toBeNull()
    view.rerender(<Shell state={{ ...state, bufferedAudio: { state: 'paused', seconds: 12.3, bytes: 345600 } }} backend={backend} />)
    expect(screen.queryByRole('textbox', { name: 'Startup silence' })).toBeNull()
    view.rerender(<Shell state={state} backend={backend} />)
    expect(screen.queryByRole('status', { name: 'Buffered playback' })).toBeNull()
    expect(screen.getByRole('textbox', { name: 'Startup silence' })).not.toBeNull()
  })

  it('hides standard startup silence in buffered mode and preserves it when switching back', async () => {
    const backend = new FakeBackend()
    backend.data.values['audio.start.silence.ms'] = '1500'
    await audio(backend)
    const pause = screen.getByRole('textbox', { name: 'Startup silence' }) as HTMLInputElement
    const fade = screen.getByRole('textbox', { name: 'Volume fade' }) as HTMLInputElement
    expect(pause.disabled).toBe(false)

    fireEvent.click(screen.getByRole('radio', { name: 'Buffered' }))
    await waitFor(() => expect(screen.queryByRole('textbox', { name: 'Startup silence' })).toBeNull())
    expect(screen.getByRole('status', { name: 'Buffered playback' })).not.toBeNull()
    expect(fade.disabled).toBe(false)
    fireEvent.change(fade, { target: { value: '500' } })
    fireEvent.blur(fade)
    await waitFor(() => expect(backend.saved).toEqual([
      { 'audio.music.mode': 'buffered' }, { 'audio.start.fade.ms': '500' },
    ]))

    fireEvent.click(screen.getByRole('radio', { name: 'Standard' }))
    const restored = await screen.findByRole('textbox', { name: 'Startup silence' }) as HTMLInputElement
    expect(restored.value).toBe('1500')
    expect(restored.disabled).toBe(false)
    fireEvent.change(restored, { target: { value: '1200' } })
    fireEvent.blur(restored)
    await waitFor(() => expect(backend.saved.at(-1)).toEqual({ 'audio.start.silence.ms': '1200' }))
  })

  it('shows buffer status when loading a saved buffered mode without a stream', async () => {
    const backend = new FakeBackend()
    backend.data.values['audio.music.mode'] = 'buffered'
    await audio(backend)
    expect(screen.queryByRole('textbox', { name: 'Startup silence' })).toBeNull()
    expect(screen.getByRole('status', { name: 'Buffered playback' })).not.toBeNull()
  })

  it('keeps the buffer row through pauses empty buffers missing snapshots and reconnects', async () => {
    const backend = new FakeBackend()
    backend.data.values['audio.music.mode'] = 'buffered'
    const state = appState({ phase: 'active', canShowCarPlay: true })
    const view = render(<Shell state={state} backend={backend} />)
    await screen.findByRole('textbox', { name: 'Network' })
    fireEvent.click(screen.getByRole('tab', { name: 'Audio' }))
    for (const bufferedAudio of [
      { state: 'playing' as const, seconds: 12.3, bytes: 345600 },
      { state: 'paused' as const, seconds: 12.3, bytes: 345600 },
      { state: 'empty' as const, seconds: 0, bytes: 0 },
      null,
      { state: 'loading' as const, seconds: 0, bytes: 0 },
      { state: 'playing' as const, seconds: 0.2, bytes: 2048 },
      null,
    ]) {
      view.rerender(<Shell state={{ ...state, bufferedAudio }} backend={backend} />)
      expect(screen.queryByRole('textbox', { name: 'Startup silence' })).toBeNull()
      expect(screen.getByRole('status', { name: 'Buffered playback' })).not.toBeNull()
    }
    view.rerender(<Shell state={{ ...state, phase: 'calling', canShowCarPlay: false }} backend={backend} />)
    expect(screen.queryByRole('textbox', { name: 'Startup silence' })).toBeNull()
    expect(screen.getByRole('status', { name: 'Buffered playback' })).not.toBeNull()
  })

  it('saves buffered music without interrupting the current session', async () => {
    const backend = await audio()
    expect(screen.getByRole('radio', { name: 'Standard' }).getAttribute('aria-checked')).toBe('true')
    fireEvent.click(screen.getByRole('radio', { name: 'Buffered' }))
    await waitFor(() => expect(backend.saved).toEqual([{ 'audio.music.mode': 'buffered' }]))
    expect(screen.getByRole('radio', { name: 'Buffered' }).getAttribute('aria-checked')).toBe('true')
    expect(backend.reconnects).toBe(0)
  })

  it('chooses the Windows default while no device is set', async () => {
    await audio()

    expect(select('Output').value).toBe('default')
  })

  it('saves a device by its name, and the Windows default as default', async () => {
    const backend = await audio()

    fireEvent.change(select('Output'), { target: { value: 'Headphones (USB Audio)' } })
    fireEvent.change(select('Microphone'), { target: { value: 'default' } })

    await waitFor(() =>
      expect(backend.saved).toEqual([{ 'audio.output': 'Headphones (USB Audio)' }, { 'audio.input': 'default' }]),
    )
  })

  // A device unplugged, or a part of a name written in the file, stays chosen rather than turning into the default.
  it('keeps a saved device the list does not have', async () => {
    const backend = new FakeBackend()
    backend.data = { ...backend.data, values: { ...backend.data.values, 'audio.output': 'Car stereo' } }
    await audio(backend)

    expect(select('Output').value).toBe('Car stereo')
  })

  it('saves echo cancellation', async () => {
    const backend = await audio()
    const echo = screen.getByRole('switch', {
      name: 'Echo cancellation',
    }) as HTMLInputElement
    expect(echo.checked).toBe(false)

    fireEvent.click(echo)

    await waitFor(() => expect(backend.saved).toEqual([{ 'audio.echo.cancellation': 'true' }]))
  })
})
