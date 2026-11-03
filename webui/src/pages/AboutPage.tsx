import icon from '@icons/chairplay-256.png'
// Official service artwork: https://cdn.buymeacoffee.com/buttons/v2/default-black.png
import coffeeButton from '../assets/buy-me-a-coffee-dark.png'
import type { ReactNode } from 'react'
import type { AppState, Backend, BuildCommit, WindowsPlace } from '../api'
import { Group, Row } from '../shell/controls'
import { CopyButton } from './messages'

function Commit({ commit }: { commit: BuildCommit | null }) {
  if (!commit) return <span className="hint">Not recorded</span>
  return (
    <>
      <span className="mono">{commit.commit}</span> · {commit.date}
    </>
  )
}

/** Which app and build this is, whose work it is built on, and where to get help. */
export function AboutPage({
  state,
  backend,
  onShowGuide,
}: {
  state: AppState
  backend: Backend
  onShowGuide: () => void
}) {
  // The links open in the browser, or the notices in Notepad; they are buttons that read as links.
  const link = (place: WindowsPlace, text: string, title?: string): ReactNode => (
    <button type="button" className="link" title={title} onClick={() => backend.open(place)}>
      {text}
    </button>
  )
  return (
    <div className="about">
      {/* The icon in the labels' column, against it as a label is; the name and line in the values' */}
      <div className="about-head">
        <img src={icon} alt="" />
        <span className="about-name">ChairPlay</span>
        <span className="hint">Wireless CarPlay receiver for Windows</span>
      </div>
      <Group title="Version" />
      <Row label="Build">
        {state.about.version && `${state.about.version} · `}
        <Commit commit={state.about.app} />
      </Row>
      <Row label="DiPlay core">
        <Commit commit={state.about.core} />
      </Row>
      <Group title="Credits" />
      <Row label="Author" className="author-credit">
        {link('author', 'Oleksiy Strelets', "Open the author's GitHub profile")}
        <button type="button" className="coffee-button" title="Open Buy Me a Coffee in your browser"
          onClick={() => backend.open('buy-me-a-coffee')}>
          <svg className="coffee-filter" width="0" height="0" aria-hidden="true">
            <defs>
              {/* Remove the artwork's #0d0c22 background; its white and yellow foreground stays opaque. */}
              <filter id="coffee-artwork" colorInterpolationFilters="sRGB">
                <feColorMatrix type="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  1.053719 0 0 -0.053719 0" />
                <feComposite in2="SourceGraphic" operator="in" />
              </filter>
            </defs>
          </svg>
          <img src={coffeeButton} alt="Buy Me a Coffee" />
        </button>
      </Row>
      <Row label="CarPlay foundation">{link('livi', 'LIVI')} by f-io</Row>
      <Row label="CarPlay core">
        {link('diplay', 'DiPlay')} by shihabal3amri, on {link('xcertplay', 'xcertplay')} by shilapi
      </Row>
      <Row label="Maneuvers, APAC">LuKa, {link('rgi', 'mib2q-carplay-rgi')}</Row>
      <Row label="License">GPL-3.0 · {link('notices', 'Third-party notices')}</Row>
      <Group title="Support" />
      <Row label="Get started">
        <button type="button" className="btn small" title="Go through the setup steps again" onClick={onShowGuide}>
          Show again
        </button>
      </Row>
      <Row label="Community">
        {link('discussions', 'Discussions', 'Ask a question or share an idea')} ·{' '}
        {link('pull-requests', 'Pull requests', 'How to send a change')}
      </Row>
      <Row label="Report a problem">
        {link('issues', 'New issue on GitHub', 'Open a new issue on GitHub, with this build filled in')}
        <span className="copy-details">
          <CopyButton
            text={state.about.details}
            label="Copy details"
            title="Copy the build, the DiPlay core, and the Windows version"
          />
        </span>
      </Row>
      <Row label="Source">
        {link('source', 'github.com/NumberOneBot/ChairPlay')} ·{' '}
        {link('releases', 'Releases', 'Every version, with what changed')}
      </Row>
      <Row className="hint-row">
        <span className="hint">
          CarPlay, iPhone, AirPlay, and Siri are trademarks of Apple Inc. ChairPlay is not affiliated with Apple.
        </span>
      </Row>
    </div>
  )
}
