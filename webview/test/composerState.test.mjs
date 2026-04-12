import assert from 'node:assert/strict'
import test from 'node:test'
import { getComposerReadiness, getConnectionStateLabel } from '../build-tests/composerState.js'

test('getComposerReadiness reports missing api key before send', () => {
  const result = getComposerReadiness({
    inputText: 'hello',
    isLoading: false,
    isBridgeReady: true,
    connectionState: 'error',
  })

  assert.deepEqual(result, {
    canSend: false,
    reason: 'API Key 未设置或未保存，请在 Settings 中重新配置并应用',
    text: 'hello',
  })
})

test('getComposerReadiness allows send when bridge is ready and configuration is valid', () => {
  const result = getComposerReadiness({
    inputText: '  hello  ',
    isLoading: false,
    isBridgeReady: true,
    connectionState: 'ready',
  })

  assert.deepEqual(result, {
    canSend: true,
    reason: null,
    text: 'hello',
  })
})

test('getConnectionStateLabel returns user-facing copy for error', () => {
  assert.equal(getConnectionStateLabel('error'), 'API key missing')
})
