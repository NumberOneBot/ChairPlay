import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Shell } from '../shell/Shell'
import { appState } from '../testing'
import { FakeBackend } from '../fakeBackend'

afterEach(cleanup)

/** The shell on the CarPlay page, once the settings are in. */
async function carplay(backend = new FakeBackend(), state = appState({ phase: 'active', canShowCarPlay: true })) {
  render(<Shell state={state} backend={backend} />)
  await screen.findByRole('textbox', { name: 'Network' })
  // The tab carries a ⟳ while one of its settings waits for a restart.
  fireEvent.click(screen.getByRole('tab', { name: /^CarPlay/ }))
  return backend
}

const textbox = (name: string) => screen.getByRole('textbox', { name }) as HTMLInputElement
const radio = (group: string, name: string) =>
  within(screen.getByRole('radiogroup', { name: group })).getByRole('radio', { name })
const chosen = (group: string) =>
  within(screen.getByRole('radiogroup', { name: group }))
    .getAllByRole('radio')
    .filter((button) => button.getAttribute('aria-checked') === 'true')
    .map((button) => button.textContent)

describe('CarPlay page, display', () => {
  it('saves codec capability and decoder preference separately', async () => {
    const backend = await carplay()
    fireEvent.click(radio('Video codec', 'H264'))
    await waitFor(() => expect(backend.saved).toEqual([{ 'video.codecs': 'h264' }]))
    fireEvent.click(radio('Decoding', 'Software'))
    await waitFor(() => expect(backend.saved).toEqual([{ 'video.codecs': 'h264' }, { 'video.decoder': 'software' }]))
    fireEvent.click(radio('Video codec', 'H265'))
    await waitFor(() => expect(backend.saved.at(-1)).toEqual({ 'video.codecs': 'h264+h265' }))
    fireEvent.click(radio('Decoding', 'Hardware'))
    await waitFor(() => expect(backend.saved.at(-1)).toEqual({ 'video.decoder': 'hardware' }))
  })

  // display.width and display.height are blank in the file: the size the app runs at is the one chosen.
  it('picks the preset of the size the app runs at, and saves a preset as both numbers', async () => {
    const backend = await carplay()
    expect(chosen('Size')).toEqual(['1280×720'])

    fireEvent.click(radio('Size', '1920×720'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'display.width': '1920', 'display.height': '720' }]))
    expect(chosen('Size')).toEqual(['1920×720'])
    expect(textbox('Width').value).toBe('1920')
  })

  it('saves a width typed in, or keeps a refused one marked', async () => {
    const backend = await carplay()

    fireEvent.change(textbox('Width'), { target: { value: '1024' } })
    fireEvent.keyDown(textbox('Width'), { key: 'Enter' })
    await waitFor(() => expect(backend.saved).toEqual([{ 'display.width': '1024' }]))

    backend.nextOutcome = { ok: false, errors: { 'display.width': 'Enter an even number of pixels' } }
    fireEvent.change(textbox('Width'), { target: { value: '1025' } })
    fireEvent.keyDown(textbox('Width'), { key: 'Enter' })

    await waitFor(() => expect(textbox('Width').getAttribute('aria-invalid')).toBe('true'))
    expect(textbox('Width').value).toBe('1025')
    expect(chosen('Size')).toEqual([])
  })

  // The window was resized: Actual size brings it back to one screen pixel per picture pixel.
  it('offers Actual size while the window differs from the picture', async () => {
    const mainWindow = {
      width: 1024,
      height: 576,
      pictureWidth: 1280,
      pictureHeight: 720,
      minWidth: 1000,
      minHeight: 600,
    }
    const backend = await carplay(new FakeBackend(), appState({ phase: 'active', canShowCarPlay: true, mainWindow }))

    fireEvent.click(screen.getByRole('button', { name: 'Actual size' }))

    expect(backend.actualSized).toEqual(['main'])
  })

  // At 150 % display scale an 800x480 picture is smaller than the window can get: the button could do nothing.
  // At 200 % display scale the window's smallest size outgrows an 800x480 picture: Actual size goes below it.
  it('offers Actual size though the picture is smaller than the window gets on its own', async () => {
    const mainWindow = {
      width: 1600,
      height: 960,
      pictureWidth: 800,
      pictureHeight: 480,
      minWidth: 1600,
      minHeight: 960,
    }
    const backend = await carplay(new FakeBackend(), appState({ phase: 'active', canShowCarPlay: true, mainWindow }))

    fireEvent.click(screen.getByRole('button', { name: 'Actual size' }))

    expect(backend.actualSized).toEqual(['main'])
  })

  // Actual size is the window's: a size chosen for the next connection stays chosen.
  it('resizes the window, leaving a size chosen for the next connection', async () => {
    const fake = new FakeBackend()
    fake.data.values['display.width'] = '1920'
    fake.data.values['display.height'] = '1080'
    const mainWindow = {
      width: 1024,
      height: 576,
      pictureWidth: 1280,
      pictureHeight: 720,
      minWidth: 1000,
      minHeight: 600,
    }
    const backend = await carplay(fake, appState({ phase: 'active', canShowCarPlay: true, mainWindow }))

    fireEvent.click(screen.getByRole('button', { name: 'Actual size' }))

    expect(backend.actualSized).toEqual(['main'])
    expect(backend.saved).toEqual([])
  })

  // A size chosen waits for the next connection, as its mark says; there is nothing to make actual yet.
  it('has no Actual size for a size waiting for the next connection', async () => {
    const fake = new FakeBackend()
    fake.data.values['display.width'] = '1920'
    const mainWindow = { width: 1280, height: 720, pictureWidth: 1280, pictureHeight: 720, minWidth: 0, minHeight: 0 }
    await carplay(fake, appState({ phase: 'active', canShowCarPlay: true, mainWindow }))

    expect(screen.queryByRole('button', { name: 'Actual size' })).toBeNull()
  })

  it('has no Actual size while the window is at it', async () => {
    const mainWindow = { width: 1280, height: 720, pictureWidth: 1280, pictureHeight: 720, minWidth: 0, minHeight: 0 }
    await carplay(new FakeBackend(), appState({ phase: 'active', canShowCarPlay: true, mainWindow }))

    expect(screen.queryByRole('button', { name: 'Actual size' })).toBeNull()
  })

  it('saves the frame rate, 60 until one is set', async () => {
    const backend = await carplay()
    expect(chosen('Frame rate')).toEqual(['60'])

    fireEvent.click(radio('Frame rate', '30'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'display.fps': '30' }]))
  })

  // With touch declared CarPlay shows scroll buttons; with none it leaves them out, and the mouse still taps and drags.
  it('saves what the touchscreen can do, None until set', async () => {
    const backend = await carplay()
    expect(chosen('Touchscreen')).toEqual(['None'])

    fireEvent.click(radio('Touchscreen', 'Swipe'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'display.touch': 'swipe' }]))
  })

  it('saves the physical size in millimetres or pixels per inch', async () => {
    const backend = await carplay()

    for (const [name, value] of [
      ['Physical width', '310'],
      ['Physical height', '175'],
      ['Pixels per inch', '160'],
    ]) {
      fireEvent.change(textbox(name), { target: { value } })
      fireEvent.blur(textbox(name))
    }

    await waitFor(() =>
      expect(backend.saved).toEqual([
        { 'display.physical.width.mm': '310' },
        { 'display.physical.height.mm': '175' },
        { 'display.ppi': '160' },
      ]),
    )
  })
})

describe('CarPlay page, look', () => {
  it("shows the app's defaults while nothing is set", async () => {
    await carplay()

    expect(chosen('Theme')).toEqual(['System'])
    expect(chosen('Dock')).toEqual(['Auto'])
    expect(chosen('Driver side')).toEqual(['Left'])
    expect(chosen('Outside safe area')).toEqual(['Wallpaper'])
    expect(chosen('Corners')).toEqual(['Square'])
  })

  it('saves each choice under its key', async () => {
    const backend = await carplay()

    fireEvent.click(radio('Theme', 'Dark'))
    fireEvent.click(radio('Dock', 'Driver side'))
    fireEvent.click(radio('Driver side', 'Right'))
    fireEvent.click(radio('Outside safe area', 'Black'))
    fireEvent.click(radio('Corners', 'Rounded'))

    await waitFor(() =>
      expect(backend.saved).toEqual([
        { 'display.theme': 'dark' },
        { 'display.dock': 'driver' },
        { 'display.right.hand.drive': 'true' },
        { 'display.safe.draw.outside': 'false' },
        { 'display.corners': 'rounded' },
      ]),
    )
  })
})

describe('CarPlay page, system theme', () => {
  // Following the system's theme is saved as system.
  it('saves the system theme as system', async () => {
    const backend = new FakeBackend()
    backend.data.values['display.theme'] = 'dark'
    await carplay(backend)

    fireEvent.click(radio('Theme', 'System'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'display.theme': 'system' }]))
  })

  // The app sends a theme it does not know (older files say windows) blank, as it reads the default: shown as system.
  it('shows a blank theme as the system theme', async () => {
    const backend = new FakeBackend()
    backend.data.values['display.theme'] = ''
    await carplay(backend)

    expect(radio('Theme', 'System').getAttribute('aria-checked')).toBe('true')
    expect(backend.saved).toEqual([])
    fireEvent.click(radio('Theme', 'Light'))

    await waitFor(() => expect(backend.saved).toEqual([{ 'display.theme': 'light' }]))
  })
})

describe('CarPlay page, changes waiting for a restart', () => {
  // The app reads the screen's settings as it starts: a changed one is marked until then, beside its control.
  it('marks each changed setting that waits for a restart', async () => {
    const state = appState({
      phase: 'active',
      canShowCarPlay: true,
      restartPending: ['display.corners', 'display.safe.top', 'display.width'],
    })
    await carplay(new FakeBackend(), state)

    const mark = (row: HTMLElement | null | undefined) => row?.querySelector('[title="Changed; applies after restart"]')
    expect(mark(radio('Corners', 'Square').closest('.row'))).toBeTruthy()
    expect(mark(textbox('Top').closest('.row'))).toBeTruthy()
    expect(mark(textbox('Width').closest('.row'))).toBeTruthy()
    expect(mark(radio('Theme', 'Dark').closest('.row'))).toBeFalsy()
  })
})

describe('CarPlay page, safe area', () => {
  it('saves each edge, and marks a refused one', async () => {
    const backend = await carplay()
    // A blank edge is no margin: it reads 0 as a saved 0 does, not as a dim hint, and leaving it saves nothing.
    expect(textbox('Top').value).toBe('0')
    fireEvent.focus(textbox('Left'))
    fireEvent.blur(textbox('Left'))

    fireEvent.change(textbox('Top'), { target: { value: '40' } })
    fireEvent.blur(textbox('Top'))
    await waitFor(() => expect(backend.saved).toEqual([{ 'display.safe.top': '40' }]))

    backend.nextOutcome = {
      ok: false,
      errors: { 'display.safe.left': 'The left and right safe areas together must be less than the width (1280 px)' },
    }
    fireEvent.change(textbox('Left'), { target: { value: '1400' } })
    fireEvent.blur(textbox('Left'))

    await waitFor(() => expect(textbox('Left').getAttribute('aria-invalid')).toBe('true'))
  })
})
