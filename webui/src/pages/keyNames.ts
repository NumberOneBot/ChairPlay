// Keys as controls.properties names them, from a key press on the Keyboard page. The file names keys after their place
// on a US keyboard (GLFW's names), so a key is read from event.code, its place, not from the character it types.

const NAMED: Record<string, string> = {
  Enter: 'Enter',
  Escape: 'Escape',
  Space: 'Space',
  Tab: 'Tab',
  Backspace: 'Backspace',
  Insert: 'Insert',
  Delete: 'Delete',
  Home: 'Home',
  End: 'End',
  PageUp: 'PageUp',
  PageDown: 'PageDown',
  CapsLock: 'CapsLock',
  ScrollLock: 'ScrollLock',
  NumLock: 'NumLock',
  PrintScreen: 'PrintScreen',
  Pause: 'Pause',
  ContextMenu: 'Menu',
  ArrowLeft: 'Left',
  ArrowRight: 'Right',
  ArrowUp: 'Up',
  ArrowDown: 'Down',
  Minus: 'Minus',
  Equal: 'Equal',
  BracketLeft: 'LeftBracket',
  BracketRight: 'RightBracket',
  Backslash: 'Backslash',
  Semicolon: 'Semicolon',
  Quote: 'Apostrophe',
  Comma: 'Comma',
  Period: 'Period',
  Slash: 'Slash',
  Backquote: 'GraveAccent',
  NumpadEnter: 'KP_Enter',
  NumpadAdd: 'KP_Add',
  NumpadSubtract: 'KP_Subtract',
  NumpadMultiply: 'KP_Multiply',
  NumpadDivide: 'KP_Divide',
  NumpadDecimal: 'KP_Decimal',
  NumpadEqual: 'KP_Equal',
  MediaTrackPrevious: 'MediaPrevious',
  MediaPlayPause: 'MediaPlayPause',
  MediaTrackNext: 'MediaNext',
}

/** Pressed alone, these are no key: the wait for one goes on. */
const MODIFIERS = new Set([
  'ShiftLeft',
  'ShiftRight',
  'ControlLeft',
  'ControlRight',
  'AltLeft',
  'AltRight',
  'MetaLeft',
  'MetaRight',
  'OSLeft',
  'OSRight',
])

export function isModifier(code: string): boolean {
  return MODIFIERS.has(code)
}

/** The key of [code] as the file names it, without modifiers; null for one the app has no name for. */
export function keyName(code: string): string | null {
  if (NAMED[code]) return NAMED[code]
  const letter = /^Key([A-Z])$/.exec(code)
  if (letter) return letter[1]
  const digit = /^Digit(\d)$/.exec(code)
  if (digit) return digit[1]
  const pad = /^Numpad(\d)$/.exec(code)
  if (pad) return `KP_${pad[1]}`
  const fn = /^F(\d{1,2})$/.exec(code)
  if (fn && Number(fn[1]) >= 1 && Number(fn[1]) <= 25) return code
  return null
}

/**
 * A key press as the file names it, Ctrl, Alt, and Shift in front in Windows' order; null for a key the app cannot
 * take (no name, or Win held, which the file has no modifier for).
 */
export function bindingName(
  event: Pick<KeyboardEvent, 'code' | 'ctrlKey' | 'altKey' | 'shiftKey' | 'metaKey'>,
): string | null {
  const key = keyName(event.code)
  if (!key || event.metaKey) return null
  return [event.ctrlKey && 'Ctrl', event.altKey && 'Alt', event.shiftKey && 'Shift', key].filter(Boolean).join('+')
}
