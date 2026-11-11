/** The Keyboard page's actions by group, in the page's order: each setting, the name the page gives it, and its tooltip. */
export const KEY_GROUPS: [group: string, actions: [key: string, label: string, title?: string][]][] = [
  [
    'CarPlay',
    [
      ['key.knob.left', 'Left', 'Tilt the knob left to move the focus left'],
      ['key.knob.right', 'Right', 'Tilt the knob right'],
      ['key.knob.up', 'Up', 'Tilt the knob up'],
      ['key.knob.down', 'Down', 'Tilt the knob down'],
      ['key.knob.next', 'Turn forward', 'Turn the knob one step forward'],
      ['key.knob.previous', 'Turn back', 'Turn the knob one step back'],
      ['key.select', 'Select', 'Press the knob'],
      ['key.back', 'Back'],
      ['key.home', 'Home', 'CarPlay home screen'],
      ['key.siri', 'Siri'],
    ],
  ],
  [
    'Media and calls',
    [
      ['key.media.previous', 'Previous track'],
      ['key.media.playpause', 'Play or pause'],
      ['key.media.next', 'Next track'],
      ['key.volume.up', 'Volume up', "ChairPlay's own volume; the Windows volume stays"],
      ['key.volume.down', 'Volume down'],
      ['key.volume.mute', 'Mute'],
      ['key.call.answer', 'Answer call'],
      ['key.call.end', 'End call'],
    ],
  ],
  [
    'AltScreen',
    [
      ['key.altscreen.content', 'HUD or map', 'Switch the AltScreen between the HUD and the map view picked last'],
      ['key.altscreen.cards', 'Move cards', 'Auto, left, right, top'],
      ['key.altscreen.zoom.in', 'Zoom in', 'Zoom the AltScreen map in'],
      ['key.altscreen.zoom.out', 'Zoom out', 'Zoom the AltScreen map out'],
    ],
  ],
  [
    'ChairPlay',
    [
      ['key.reconnect', 'Reconnect', 'End the CarPlay session gracefully and connect again'],
      ['key.screenshot', 'Screenshot', 'Save the CarPlay screen as a PNG file in the screenshots folder'],
      ['key.fullscreen', 'Full screen', 'Toggle full screen for the focused CarPlay or AltScreen window'],
      ['key.drive', 'Drive mode', 'Drive the GPS car with the arrow keys instead of moving through CarPlay, until pressed again'],
      ['key.gauges', 'Gauge size', "Switch the GPS car's gauges between large and small"],
    ],
  ],
]
