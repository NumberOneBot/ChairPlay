import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { PrefixField } from './PrefixField'

afterEach(cleanup)

const port = () => screen.getByRole('textbox', { name: 'Port' }) as HTMLInputElement

it('saves what is typed after the fixed start, on Enter or as the field is left', async () => {
  const commit = vi.fn(async () => true)
  render(<PrefixField prefix="http://127.0.0.1:" label="Port" value="7529" onCommit={commit} />)
  expect(port().value).toBe('7529')

  fireEvent.change(port(), { target: { value: '8080' } })
  fireEvent.keyDown(port(), { key: 'Enter' })
  await waitFor(() => expect(commit).toHaveBeenCalledWith('8080'))

  fireEvent.change(port(), { target: { value: '8081' } })
  fireEvent.blur(port())
  await waitFor(() => expect(commit).toHaveBeenLastCalledWith('8081'))
})

it('keeps a refused value, marked, with its reason', () => {
  render(<PrefixField prefix="http://127.0.0.1:" label="Port" value="70000" error="Enter a port from 1 to 65535" onCommit={() => false} />)

  expect(port().getAttribute('aria-invalid')).toBe('true')
  expect(port().value).toBe('70000')
  expect(screen.getByText('Enter a port from 1 to 65535')).toBeTruthy()
})
