// The support card's part of the app's API (AppUiApi.kt): its state and its buttons' answer.

/** Buy me a coffee or I already did ('done'): never asked again; Later: asked again in three days. */
export type SupportAction = 'done' | 'later'

export interface SupportState {
  /** The card asks for a coffee: two hours of CarPlay, not supported, and Later not pressed in the last three days. */
  supportDue: boolean
}

export interface SupportBackend {
  /** Stores the card's answer; the card goes with the next state. */
  support(action: SupportAction): Promise<boolean>
}
