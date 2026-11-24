import { currentPlatform, type PlatformId } from './platform'

/**
 * A text that differs by system: the system's own where it has one, else the shared `default`. Systems without their
 * own settings pages written here yet (Linux, macOS) get the shared text, which names no settings path.
 */
type Variants = Partial<Record<PlatformId, string>> & ({ default: string } | Record<PlatformId, string>)

/** The page's texts that name the system, by key; English only. `{name}` is a parameter [t] fills in. */
export const TEXTS = {
  'about.tagline': {
    windows: 'Wireless CarPlay receiver for Windows',
    linux: 'Wireless CarPlay receiver for Linux',
    macos: 'Wireless CarPlay receiver for macOS',
  },
  'about.copyDetails': {
    windows: 'Copy the build, the DiPlay core, and the Windows version',
    default: 'Copy the build, the DiPlay core, and the system version',
  },
  'audio.systemDefault': { windows: 'Windows default', default: 'System default' },
  'audio.volume': {
    windows: "ChairPlay's own volume, as the volume keys and the controls window set it; Windows' own stays as it is",
    default: "ChairPlay's own volume, as the volume keys and the controls window set it; the system's own stays as it is",
  },
  'keys.volumeUp': {
    windows: "ChairPlay's own volume; the Windows volume stays",
    default: "ChairPlay's own volume; the system volume stays",
  },
  'firewall.addRule': {
    windows: 'Add an inbound rule for ChairPlay.exe; Windows asks for your consent',
    default: 'Add an inbound rule for ChairPlay; the system asks for your consent',
  },
  'firewall.letIn': {
    windows: 'If the iPhone cannot connect, let ChairPlay in through Windows Firewall.',
    default: 'If the iPhone cannot connect, let ChairPlay in through the firewall.',
  },
  'firewall.openRules': {
    windows: 'Open Windows Defender Firewall with Advanced Security',
    default: "Open the firewall's rules",
  },
  'firewall.blocked': {
    windows: 'A rule in Windows Defender Firewall blocks ChairPlay, so the iPhone cannot reach it. Delete the rule, or ' +
      'set it to Allow, then come back.',
    default: 'A firewall rule blocks ChairPlay, so the iPhone cannot reach it. Delete the rule, or set it to Allow, ' +
      'then come back.',
  },
  'firewall.blockedHow': {
    windows: "Delete the rule, or set it to Allow, in the firewall's Inbound Rules.",
    default: "Delete the rule, or set it to Allow, in the firewall's rules.",
  },
  'firewall.runAsAdmin': {
    windows: 'If the iPhone cannot connect, run this in Terminal (Admin):',
    default: 'If the iPhone cannot connect, run this as an administrator:',
  },
  'firewall.noRule': {
    windows: "Windows Firewall is on and has no rule for ChairPlay, so it may drop the iPhone's connections.",
    default: "The firewall is on and has no rule for ChairPlay, so it may drop the iPhone's connections.",
  },
  'pair.forgotten': {
    windows: 'ChairPlay forgot {name}, but Windows still has it paired. To pair from scratch, remove it in ' +
      'Settings › Bluetooth & devices, and forget this car on the iPhone in Settings › General › CarPlay.',
    default: 'ChairPlay forgot {name}, but this PC still has it paired. To pair from scratch, remove it in the ' +
      "PC's Bluetooth settings, and forget this car on the iPhone in Settings › General › CarPlay.",
  },
  'pair.how': {
    windows: 'Turn on Bluetooth on the iPhone, then pair it with this PC in Settings › Bluetooth & devices › Add device.',
    default: "Turn on Bluetooth on the iPhone, then pair it with this PC in the PC's Bluetooth settings.",
  },
  'pair.howCard': {
    windows: 'On the iPhone, open Settings › Bluetooth. On this PC, open Settings › Bluetooth & devices › Add device › ' +
      'Bluetooth, choose the iPhone, and confirm the code on both.',
    default: "On the iPhone, open Settings › Bluetooth. On this PC, add the iPhone in the PC's Bluetooth settings, and " +
      'confirm the code on both.',
  },
  'pair.stillPaired': { windows: 'Still paired in Windows', default: 'Still paired on this PC' },
  'error.bluetooth': {
    windows: 'Check that the iPhone is paired in Windows Bluetooth settings, nearby, and has Bluetooth on.',
    default: "Check that the iPhone is paired in the PC's Bluetooth settings, nearby, and has Bluetooth on.",
  },
  'network.wifiHidden': {
    windows: "Windows hides this PC's Wi-Fi network from ChairPlay.",
    default: "The system hides this PC's Wi-Fi network from ChairPlay.",
  },
  'network.wifiHiddenHow': {
    windows: 'On this PC, open Settings › Privacy & security › Location and turn on Let desktop apps access your location.',
    default: "On this PC, let apps access your location in the PC's location settings.",
  },
  'network.locationFirst': {
    windows: 'Turn on location for desktop apps first',
    default: 'Turn on location for apps first',
  },
  'network.adapterUnread': {
    windows: 'Could not read Ethernet adapters. Check the adapter in Windows network settings.',
    default: "Could not read Ethernet adapters. Check the adapter in the PC's network settings.",
  },
  'network.adapterFirst': { windows: 'Check the network adapter in Windows first', default: 'Check the network adapter first' },
  'location.use': { windows: 'Use Windows location', default: "Use this PC's location" },
  'location.useTitle': {
    windows: 'Put the car where Windows locates this PC',
    default: 'Put the car where the system locates this PC',
  },
  'location.denied': {
    windows: "Windows does not share this PC's location.",
    default: "The system does not share this PC's location.",
  },
  'location.turnOn': { windows: 'Turn it on for desktop apps in', default: 'Turn it on in' },
  'location.unknown': {
    windows: 'Windows found no position. Try again in a moment.',
    default: 'The system found no position. Try again in a moment.',
  },
} satisfies Record<string, Variants>

export type TextKey = keyof typeof TEXTS

/** The text [key] for the system the app runs on, its `{param}`s filled in from [params]. */
export function t(key: TextKey, params: Record<string, string> = {}): string {
  const entry: Partial<Record<PlatformId | 'default', string>> = TEXTS[key]
  const text = entry[currentPlatform()] ?? entry.default ?? ''
  return text.replace(/\{(\w+)\}/g, (whole, name: string) => params[name] ?? whole)
}
