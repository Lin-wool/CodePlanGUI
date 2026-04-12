import assert from 'node:assert/strict'
import test from 'node:test'
import { getContextToggleMeta } from '../build-tests/contextState.js'

test('getContextToggleMeta keeps footer compact when a context summary exists', () => {
  assert.deepEqual(
    getContextToggleMeta(true, 'ChatService.kt · 选中 18 行'),
    {
      label: 'context on',
      title: 'ChatService.kt · 选中 18 行',
    },
  )
})

test('getContextToggleMeta explains when there is no file context to attach', () => {
  assert.deepEqual(
    getContextToggleMeta(true, ''),
    {
      label: 'no open file',
      title: '当前没有可附加的文件上下文',
    },
  )
})

test('getContextToggleMeta reports context off without file duplication', () => {
  assert.deepEqual(
    getContextToggleMeta(false, 'ChatService.kt · 当前文件'),
    {
      label: 'context off',
      title: '不附加文件上下文',
    },
  )
})
