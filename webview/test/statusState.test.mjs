import assert from 'node:assert/strict'
import test from 'node:test'
import { applyBridgeStatus, applyContextFile } from '../build-tests/statusState.js'

test('applyBridgeStatus preserves the current context file when status payload omits it', () => {
  const previous = {
    providerName: 'OpenAI',
    model: 'gpt-5.4',
    connectionState: 'ready',
    contextFile: 'ChatPanel.kt',
  }

  const next = applyBridgeStatus(previous, {
    providerName: 'OpenAI',
    model: 'gpt-5.4',
    connectionState: 'streaming',
  })

  assert.deepEqual(next, {
    providerName: 'OpenAI',
    model: 'gpt-5.4',
    connectionState: 'streaming',
    contextFile: 'ChatPanel.kt',
  })
})

test('applyContextFile updates only the context file field', () => {
  const previous = {
    providerName: 'OpenAI',
    model: 'gpt-5.4',
    connectionState: 'ready',
    contextFile: '',
  }

  const next = applyContextFile(previous, 'BridgeHandler.kt')

  assert.deepEqual(next, {
    providerName: 'OpenAI',
    model: 'gpt-5.4',
    connectionState: 'ready',
    contextFile: 'BridgeHandler.kt',
  })
})
