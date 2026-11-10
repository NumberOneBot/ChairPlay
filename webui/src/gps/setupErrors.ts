// The wizard's step errors: a download that broke has its own words; any other error is one generic error.
const DOWNLOAD: readonly [reason: string, instruction: string] = [
  'Download interrupted',
  'Check your Internet connection, then try again.',
]
const GENERIC: readonly [reason: string, instruction: string] = ['Something went wrong', 'Try again.']

/** The reason and the instruction for an error code. */
export function setupError(code: string): readonly [reason: string, instruction: string] {
  return code === 'download-failed' ? DOWNLOAD : GENERIC
}
