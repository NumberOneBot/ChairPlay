import type { ReactNode } from 'react'

/** The system the app runs on (Platform.id in the app). */
export type PlatformId = 'windows' | 'linux' | 'macos'

/** A part of the app only some systems have (Feature.key in the app). */
export type Feature = 'usbGps' | 'gameBridge' | 'globalKeyboard' | 'location'

export const ALL_FEATURES: Feature[] = ['usbGps', 'gameBridge', 'globalKeyboard', 'location']

/** The app state's system, what it has, and which of its own pages it opens; the page picks its texts and parts by them. */
export interface PlatformState {
  platform: PlatformId
  features: Feature[]
  /** The system pages the app can open here, by the names `open` takes (UiPlatform.kt's SYSTEM_PLACES). */
  systemPages: string[]
}

/** Windows, which has every feature and page: today's page. */
export const WINDOWS: PlatformState = {
  platform: 'windows',
  features: ALL_FEATURES,
  systemPages: ['bluetooth', 'wifi', 'firewall', 'firewall-rules', 'location', 'apple-devices-app'],
}

// One system per run, so the page keeps it here for texts made outside React too; Windows until the state comes (and
// in tests).
let current: PlatformState = WINDOWS

/** Called by App with each state; the system never changes while the app runs. */
export function setPlatform({ platform, features, systemPages }: PlatformState) {
  current = { platform, features, systemPages }
}

export function currentPlatform(): PlatformId {
  return current.platform
}

export function supports(feature: Feature): boolean {
  return current.features.includes(feature)
}

/** Whether the app can open the system's own [place] page here. */
export function opens(place: string): boolean {
  return current.systemPages.includes(place)
}

/** Its children where the system has [feature], nothing elsewhere. */
export function WhenSupported({ feature, children }: { feature: Feature; children: ReactNode }) {
  return supports(feature) ? <>{children}</> : null
}
