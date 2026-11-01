// Development-only entry point: real screens, simulated adapters, no desktop API or network changes.
import { useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { FakeBackend } from './fakeBackend'
import { appState } from './testing'
import { Shell } from './shell/Shell'
import './styles/index.css'

function Preview() {
  const query = new URLSearchParams(location.search)
  const count = Number(query.get('adapters') ?? 2)
  const initial = appState({ phase: 'idle', setupDone: query.get('screen') === 'settings' })
  initial.wifi = { ssid: 'Home', security: 'WPA_WPA2', passphraseSet: false }
  initial.setup = { ...initial.setup, mfiPresent: true, firewall: 'present', pairedIphone: 'iPhone', pcWifi: null,
    ethernet: [
      { id: '11111111-1111-1111-1111-111111111111', label: 'Ethernet', addresses: ['192.168.1.42'] },
      { id: '22222222-2222-2222-2222-222222222222', label: 'USB Ethernet', addresses: ['10.0.0.15'] },
    ].slice(0, count), ethernetSelected: count === 1 ? '11111111-1111-1111-1111-111111111111' : null }
  const [state, setState] = useState(initial)
  const backend = useMemo(() => {
    const fake = new FakeBackend()
    fake.data.passphraseSet = false
    const save = fake.save.bind(fake)
    fake.save = async changes => {
      const result = await save(changes)
      fake.data = { ...fake.data, values: { ...fake.data.values, ...changes } }
      setState(previous => ({ ...previous, settingsVersion: previous.settingsVersion + 1,
        setup: { ...previous.setup, ethernetSelected: changes['network.ethernet.adapter'] ?? previous.setup.ethernetSelected },
        wifi: { ssid: changes['wifi.ssid'] ?? previous.wifi.ssid,
          security: changes['wifi.security'] ?? previous.wifi.security,
          passphraseSet: changes['wifi.passphrase'] !== undefined ? changes['wifi.passphrase'] !== '' : previous.wifi.passphraseSet } }))
      return result
    }
    return fake
  }, [])
  return <>
    <div style={{ padding: '8px 12px', font: '13px sans-serif', background: '#243343', color: '#fff' }}>
      Ethernet preview · Simulated adapters · <a href="?adapters=1">One</a> · <a href="?adapters=2">Several</a> ·{' '}
      <a href="?adapters=0">None</a> · <a href={`?adapters=${count}&screen=settings`}>Settings</a> ·{' '}
      <a href={`?adapters=${count}`}>Get started</a>
    </div>
    <Shell state={state} backend={backend} />
  </>
}
createRoot(document.getElementById('root')!).render(<Preview />)
