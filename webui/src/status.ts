import type { AppState } from './api'

export type Tone = 'ok' | 'busy' | 'warn' | 'idle'

export interface Status {
  title: string
  detail: string
  tone: Tone
}

/** The start screen's headline for where the connection stands. */
export function describe(state: Pick<AppState, 'phase' | 'knownIphone'>): Status {
  switch (state.phase) {
    case 'setup':
      return { title: 'Wi-Fi network needed', detail: 'Enter the network this PC and the iPhone share.', tone: 'warn' }
    case 'idle':
      return { title: 'Not connected', detail: 'Press Connect when the iPhone is nearby.', tone: 'idle' }
    case 'starting':
      return { title: 'Connecting…', detail: "Preparing this PC's Wi-Fi and the CarPlay service.", tone: 'busy' }
    case 'calling':
      return {
        title: 'Waiting for the iPhone…',
        detail: 'Calling it over Bluetooth. Keep its Bluetooth and Wi-Fi on.',
        tone: 'busy',
      }
    case 'joining':
      return {
        title: 'Connecting…',
        // The Bluetooth half is done by now: the iPhone answered and took the Wi-Fi network.
        detail: state.knownIphone
          ? 'Paired over Bluetooth. The iPhone is joining the Wi-Fi.'
          : 'Paired over Bluetooth. Accept CarPlay on the iPhone if it asks.',
        tone: 'busy',
      }
    case 'active':
      return { title: 'Connected', detail: 'CarPlay is running.', tone: 'ok' }
    case 'retrying':
      return { title: 'Trying again', detail: 'Next attempt in a few seconds.', tone: 'warn' }
  }
}

/**
 * What a bring-up error is about: the MFi files, an iPhone that answered over Bluetooth but did not join the Wi-Fi
 * (most often a wrong password), Bluetooth, the Wi-Fi, or something else.
 */
export type ErrorKind = 'mfi' | 'join' | 'bluetooth' | 'wifi' | 'other'

export function errorKind(message: string): ErrorKind {
  const text = message.toLowerCase()
  if (text.includes('mfi') || text.includes('identity.pk8') || text.includes('certificate.p7b')) return 'mfi'
  // WirelessBootstrap's "Bluetooth control ended … before the iPhone joined over Wi-Fi": checked before Bluetooth.
  if (text.includes('before the iphone joined')) return 'join'
  if (text.includes('rfcomm') || text.includes('bluetooth') || text.includes('paired')) return 'bluetooth'
  if (text.includes('wi-fi') || text.includes('wlan') || text.includes('network') || text.includes('ssid'))
    return 'wifi'
  return 'other'
}

/** A hint for an error the bring-up reported, or null when there is none to give. */
export function errorHint(message: string, setup: Pick<AppState['setup'], 'mfiFolder'>): string | null {
  switch (errorKind(message)) {
    case 'mfi':
      return `Put the MFi identity files (identity.pk8 and certificate.p7b) into ${setup.mfiFolder}.`
    case 'bluetooth':
      return 'Check that the iPhone is paired in Windows Bluetooth settings, nearby, and has Bluetooth on.'
    case 'join':
    case 'wifi':
      return 'Check the Wi-Fi network below, and that this PC is connected to it.'
    case 'other':
      return null
  }
}
