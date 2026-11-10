import { useEffect, useState } from 'react'
import type { Backend } from '../api'
import type { GpsNotice, GpsOutput } from '../gpsApi'
import { Message } from '../shell/controls'
import { noticeText, operationHead } from './noticeText'

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

/** The one status line of the operation under way; nothing without one. */
export function OperationNotice({ output }: { output: GpsOutput }) {
  if (!output.operation) return null
  return <Message tone="info" head={operationHead(output.operation)} text="" />
}

/** One notice as a block. A reconnect that waits is the settings' own mark, not a notice. */
export function NoticeBlock({ notice }: { notice: GpsNotice; backend?: Backend }) {
  const { head, text } = noticeText(notice)
  return <Message tone={notice.tone} head={head} text={text} />
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
          <NoticeBlock key={notice.id} notice={notice} backend={backend} />
        ))}
    </>
  )
}

/** Over the content of whichever section is open, outside its scroll: the warning that USB GPS waits for the cable. */
export function GlobalNotices({ output, backend }: { output?: GpsOutput; backend: Backend }) {
  const notices = useLiveNotices(output?.notices ?? [])
  const usb = notices.find(({ code }) => code === 'usb-unavailable')
  return <div className="global-notices">{usb && <NoticeBlock notice={usb} backend={backend} />}</div>
}
