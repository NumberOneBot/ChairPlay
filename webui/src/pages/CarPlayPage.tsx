import type { AppState, Backend } from '../api'
import { Group, Row, Switch } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { AlwaysOnTop } from './AlwaysOnTop'
import { ActualSize, Changed, parts, pendingOf, SafeArea, SizeRows } from './parts'

/** Common car and desktop display sizes, the 480 px tall ones first. */
const PRESETS = [
  [800, 480],
  [1024, 480],
  [1280, 480],
  [1280, 720],
  [1920, 720],
  [1080, 1080],
  [1920, 1080],
  [2560, 1080],
  [2560, 1440],
] as const

const DISPLAY_KEYS = [
  'display.width',
  'display.height',
  'display.fps',
  'video.codecs',
  'video.decoder',
  'display.physical.width.mm',
  'display.physical.height.mm',
  'display.ppi',
]
const LOOK_KEYS = [
  'display.theme',
  'display.dock',
  'display.right.hand.drive',
  'display.safe.draw.outside',
  'display.corners',
  'display.touch',
]

/** The picture CarPlay draws: its size and frame rate, how large it is, its look, and the safe area. */
export function CarPlayPage({ state, backend, settings }: { state: AppState; backend: Backend; settings: Settings }) {
  const pending = pendingOf(state)
  // 1.0.x files say windows for the system theme, as the app still reads it: shown as system, the file left as it is.
  const value = (key: string) => {
    const stored = settings.value(key)
    return key === 'display.theme' && stored.trim().toLowerCase() === 'windows' ? 'system' : stored
  }
  const { field, choice, note } = parts({ ...settings, value }, pending)
  const displayFallback = settings.data?.display ?? { width: 1280, height: 720 }
  return (
    <>
      <Group title="Display" note={note(DISPLAY_KEYS)} />
      {/* Blank in the file: the size the app runs at, which is its default. */}
      <SizeRows
        settings={settings}
        prefix="display"
        presets={PRESETS}
        columns={3}
        fallback={displayFallback}
        after={<ActualSize sizes={state.mainWindow} window="main" backend={backend} />}
        pending={pending}
      />
      <Row label="Physical size" title="Leave blank for 200 mm wide, with the height from the aspect ratio">
        {field('display.physical.width.mm', 'Physical width', '200', 'short c')}
        <span className="x times">×</span>
        {field('display.physical.height.mm', 'Physical height', 'auto', 'short c')}
        <span className="unit">mm</span>
        <span className="x">or</span>
        {field('display.ppi', 'Pixels per inch', 'auto', 'short c')}
        <span className="unit">ppi</span>
        <Changed keys={['display.physical.width.mm', 'display.physical.height.mm', 'display.ppi']} pending={pending} />
      </Row>
      <Row label="Frame rate">
        {choice(
          'display.fps',
          'Frame rate',
          '60',
          [
            ['30', '30'],
            ['60', '60'],
          ],
          'half',
          'fps',
        )}
      </Row>
      <Row label="Video codec" title="Codecs offered to the iPhone for both screens">
        {choice('video.codecs', 'Video codec', 'h264+h265', [
          ['h264+h265', 'H265'],
          ['h264', 'H264'],
        ])}
      </Row>
      <Row label="Decoding" title="Hardware decoding automatically falls back to software if the GPU is unavailable or decoding fails">
        {choice('video.decoder', 'Decoding', 'hardware', [
          ['hardware', 'Hardware', 'Use the GPU for both screens; fall back to software if unavailable or decoding fails'],
          ['software', 'Software', 'Decode both screens on the CPU'],
        ])}
      </Row>
      <Row className="hint-row">
        <span className="hint">
          With H265 selected, both codecs are offered and the iPhone chooses which to use
        </span>
      </Row>
      <Row className="hint-row">
        <span className="hint">
          Hardware decoding automatically falls back to software if the GPU is unavailable or decoding fails
        </span>
      </Row>
      <AlwaysOnTop settings={settings} setting="window.always.on.top" />
      <Row title="Show the CarPlay window and bring it to the front when a call comes in. Its taskbar button flashes on a call either way.">
        <Switch
          label="Bring to front on a call"
          checked={settings.value('window.front.on.call') === 'true'}
          onChange={(checked) => settings.save({ 'window.front.on.call': String(checked) })}
        />
      </Row>
      <Group title="Appearance" note={note(LOOK_KEYS)} />
      <Row label="Theme">
        {choice('display.theme', 'Theme', 'system', [
          ['system', 'System'],
          ['phone', 'Phone'],
          ['light', 'Light'],
          ['dark', 'Dark'],
        ])}
      </Row>
      <Row
        label="Touchscreen"
        title="The touchscreen CarPlay is told of: with one, CarPlay shows scroll buttons in its lists"
      >
        {choice('display.touch', 'Touchscreen', 'none', [
          [
            'none',
            'None',
            'No touchscreen: CarPlay leaves out its scroll buttons; the mouse still taps and drags, the keys move the focus',
          ],
          [
            'swipe',
            'Swipe',
            'A touchscreen that swipes: CarPlay shows scroll buttons, and lists also scroll by swiping',
          ],
          ['tap', 'Tap only', 'A touchscreen that only taps: CarPlay shows scroll buttons for its lists'],
        ])}
      </Row>
      <Row label="Dock">
        {choice('display.dock', 'Dock', 'auto', [
          ['auto', 'Auto'],
          ['driver', 'Driver side'],
          ['bottom', 'Bottom'],
        ])}
      </Row>
      <Row label="Driver side" title="The side the driver sits on: CarPlay puts its dock and controls there">
        {choice('display.right.hand.drive', 'Driver side', 'false', [
          ['false', 'Left'],
          ['true', 'Right'],
        ])}
      </Row>
      <Row label="Outside safe area" title="What CarPlay draws outside the safe area (drawUIOutsideSafeArea)">
        {choice('display.safe.draw.outside', 'Outside safe area', 'true', [
          ['false', 'Black'],
          ['true', 'Wallpaper'],
        ])}
      </Row>
      <Row label="Corners">
        {choice('display.corners', 'Corners', 'square', [
          ['square', 'Square'],
          ['rounded', 'Rounded'],
        ])}
      </Row>
      <Group title="Safe area" note="Pixels at each edge" />
      <SafeArea settings={settings} prefix="display.safe" label="Insets" pending={pending} />
    </>
  )
}
