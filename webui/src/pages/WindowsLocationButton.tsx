import { useState } from 'react'
import type { Backend } from '../api'
import { opens } from '../i18n/platform'
import { t } from '../i18n/texts'
import { SystemPageButton } from '../shell/SystemPageButton'
import type { Settings } from '../shell/useSettings'

/**
 * Puts the GPS car where Windows locates this PC, facing [heading] as it does now, as typed coordinates do. Without a
 * place it says why: location off or not let for desktop apps, with a way to its settings, or no fix yet.
 */
export function WindowsLocationButton({
  backend,
  settings,
  heading,
}: {
  backend: Backend
  settings: Settings
  heading: string
}) {
  const [asking, setAsking] = useState(false)
  const [problem, setProblem] = useState<'denied' | 'unknown' | null>(null)

  async function locate() {
    setAsking(true)
    setProblem(null)
    try {
      const found = await backend.windowsLocation()
      if ('error' in found) setProblem(found.error)
      else void settings.save({ 'gps.start': `${found.latitude}, ${found.longitude}`, 'gps.heading': heading })
    } finally {
      setAsking(false)
    }
  }

  return (
    <>
      <button
        type="button"
        className="btn small"
        title={t('location.useTitle')}
        disabled={asking}
        onClick={() => void locate()}
        data-testid="gps-locate"
      >
        {t('location.use')}
      </button>
      {problem === 'denied' && (
        <span className="errtext">
          {t('location.denied')}
          {/* The way to turn it on only where the app can open the system's location settings. */}
          {opens('location') && <>
            {' '}{t('location.turnOn')}{' '}
            <SystemPageButton place="location" backend={backend} className="link">Location settings</SystemPageButton>
          </>}
        </span>
      )}
      {problem === 'unknown' && <span className="errtext">{t('location.unknown')}</span>}
    </>
  )
}
