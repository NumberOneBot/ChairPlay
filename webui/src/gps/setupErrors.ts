// The wizard's step errors: the app reports a code (a UsbErrorCode), the page words the reason, then what to do.
const ERRORS: Record<string, readonly [reason: string, instruction: string]> = {
  'download-failed': ['Download interrupted', 'Check your Internet connection, then try again.'],
  'disk-full': ['Not enough disk space', 'Free some space on this PC, then try again.'],
  'verification-failed': ['The download did not pass verification', 'Download it again.'],
  'mount-failed': ['Could not connect the package', 'Unlock your iPhone and keep it on the cable, then try again.'],
  'service-unavailable': ['The developer service is unavailable', 'Restart your iPhone, then try again.'],
  'store-unavailable': ['Microsoft Store is unavailable', 'Check your Internet connection, then try again.'],
  'several-devices': ['Several iPhones are connected', 'Disconnect all but one, then try again.'],
  'unsupported-ios': ['This iOS version is not supported', 'Update your iPhone, then try again.'],
  timeout: ['Your iPhone did not answer', 'Unlock your iPhone, then try again.'],
}

/** The reason and the instruction for an error code; one for any code the page does not know. */
export function setupError(code: string): readonly [reason: string, instruction: string] {
  return ERRORS[code] ?? ['Something went wrong', 'Try again.']
}
