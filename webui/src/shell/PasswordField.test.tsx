import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { PasswordField } from './fields'

afterEach(cleanup)

const input = () => screen.getByLabelText('Password') as HTMLInputElement
const eye = (show: boolean) => screen.getByRole('button', { name: show ? 'Show the password' : 'Hide the password' })

it('keeps the replacement after saving a revealed password', async () => {
  let stored = 'old-password'
  const commit = vi.fn(async (value: string) => { stored = value; return true })
  render(<PasswordField label="Password" set saved={async () => stored} onCommit={commit} />)
  fireEvent.click(eye(true))
  await waitFor(() => expect(input().value).toBe('old-password'))

  fireEvent.change(input(), { target: { value: 'new-password' } })
  fireEvent.keyDown(input(), { key: 'Enter' })
  await waitFor(() => expect(commit).toHaveBeenCalledWith('new-password'))
  await act(async () => {})
  expect(input().value).toBe('new-password')
  fireEvent.blur(input())
  expect(commit).toHaveBeenCalledTimes(1)
  expect(stored).toBe('new-password')
})

it('does not reveal the old password while a replacement is being saved on blur', async () => {
  let stored = 'old-password'
  let finish!: () => void
  const saving = new Promise<boolean>((resolve) => {
    finish = () => { stored = 'new-password'; resolve(true) }
  })
  render(<PasswordField label="Password" set saved={async () => stored} onCommit={() => saving} />)
  fireEvent.change(input(), { target: { value: 'new-password' } })
  fireEvent.blur(input())
  fireEvent.click(eye(true))
  await act(async () => {})
  expect(input().value).toBe('new-password')
  await act(async () => finish())
  expect(input().value).toBe('new-password')
})

it('keeps a refused replacement for correction', async () => {
  render(<PasswordField label="Password" set saved={async () => 'old-password'} onCommit={async () => false} />)
  fireEvent.click(eye(true))
  await waitFor(() => expect(input().value).toBe('old-password'))
  fireEvent.change(input(), { target: { value: 'short' } })
  fireEvent.keyDown(input(), { key: 'Enter' })
  await act(async () => {})
  expect(input().value).toBe('short')
})

it('ignores a stale password read that completes after saving the replacement', async () => {
  let finishRead!: (value: string) => void
  const reading = new Promise<string>((resolve) => { finishRead = resolve })
  render(<PasswordField label="Password" set saved={() => reading} onCommit={async () => true} />)
  fireEvent.click(eye(true))
  fireEvent.change(input(), { target: { value: 'new-password' } })
  fireEvent.keyDown(input(), { key: 'Enter' })
  await act(async () => {})
  await act(async () => finishRead('old-password'))
  expect(input().value).toBe('new-password')
})

it('does not reveal a replacement when its save completes after hiding it', async () => {
  let finish!: (value: boolean) => void
  const saving = new Promise<boolean>((resolve) => { finish = resolve })
  render(<PasswordField label="Password" set saved={async () => 'old-password'} onCommit={() => saving} />)
  fireEvent.click(eye(true))
  await waitFor(() => expect(input().value).toBe('old-password'))
  fireEvent.change(input(), { target: { value: 'new-password' } })
  fireEvent.blur(input())
  fireEvent.click(eye(false))
  await act(async () => finish(true))
  expect(input().type).toBe('password')
  expect(input().value).toBe('')
})
