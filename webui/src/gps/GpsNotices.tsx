import { useEffect, useState } from 'react'
import type { Backend } from '../api'
import type { GpsNotice, GpsOutput } from '../gpsApi'
import { Message } from '../shell/controls'
import { noticeText, operationText } from './noticeText'

/** The notices not yet gone: one with an expiresAt leaves the page then, by the clock the page has. */
export function useLiveNotices(notices: GpsNotice[]): GpsNotice[] {
  const [, tick] = useState(0)
  const next = notices.reduce<number | null>(
    (soonest, { expiresAt }) =>
      expiresAt !== null && expiresAt > Date.now() && (soonest === null || expiresAt < soonest) ? expiresAt : soonest,
    null,
  )
  useEffect(() => {
    if (next === null) return
    const timer = setTimeout(() => tick((count) => count + 1), Math.max(0, next - Date.now()) + 1)
    return () => clearTimeout(timer)
  }, [next])
  return notices.filter(({ expiresAt }) => expiresAt === null || expiresAt > Date.now())
}

/** The progress of the operation running, as one information block; nothing without one. */
export function OperationNotice({ output }: { output: GpsOutput }) {
  if (!output.operation) return null
  const { head, text } = operationText(output.operation, output.selected)
  return <Message tone="info" head={head} text={text} />
}

/** One notice as a block; Reconnect is the action of the one that asks for it. */
export function NoticeBlock({
  notice,
  backend,
  busy = false,
}: {
  notice: GpsNotice
  backend: Backend
  /** An operation holds the window: the action waits. */
  busy?: boolean
}) {
  const { head, text } = noticeText(notice)
  return (
    <Message tone={notice.tone} head={head} text={text}>
      {notice.code === 'reconnect-needed' && (
        <div className="acts">
          <button type="button" className="btn small" title={busy ? 'Wait for the operation to end' : 'End the session and connect again'} disabled={busy} onClick={() => void backend.reconnect()}>
            Reconnect
          </button>
        </div>
      )}
    </Message>
  )
}

/** Above the GPS switch: the operation's progress, then the results; the USB warning is the window's (GlobalNotices). */
export function GpsNotices({ output, backend }: { output: GpsOutput; backend: Backend }) {
  const notices = useLiveNotices(output.notices)
  return (
    <>
      <OperationNotice output={output} />
      {notices
        .filter(({ code }) => code !== 'usb-unavailable')
        .map((notice) => (
          <NoticeBlock key={notice.id} notice={notice} backend={backend} busy={output.operation !== null} />
        ))}
    </>
  )
}

/**
 * Over the content of whichever section is open, outside its scroll: the warning that USB GPS waits for the cable, and,
 * on a section other than GPS, the operation that holds the window, so that it is clear why nothing answers.
 */
export function GlobalNotices({ output, backend, onGps }: { output?: GpsOutput; backend: Backend; onGps: boolean }) {
  const notices = useLiveNotices(output?.notices ?? [])
  if (!output) return null
  const usb = notices.find(({ code }) => code === 'usb-unavailable')
  return (
    <div className="global-notices">
      {usb && <NoticeBlock notice={usb} backend={backend} />}
      {!onGps && <OperationNotice output={output} />}
    </div>
  )
}
