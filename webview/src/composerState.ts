type ConnectionState = 'unconfigured' | 'ready' | 'streaming' | 'error'

type ComposerReadinessInput = {
  inputText: string
  isLoading: boolean
  isBridgeReady: boolean
  connectionState: ConnectionState
}

type ComposerReadiness = {
  canSend: boolean
  reason: string | null
  text: string
}

const connectionStateLabels: Record<ConnectionState, string> = {
  unconfigured: 'Provider not configured',
  ready: 'Ready',
  streaming: 'Streaming response',
  error: 'API key missing',
}

export function getConnectionStateLabel(state: ConnectionState): string {
  return connectionStateLabels[state]
}

export function getComposerReadiness({
  inputText,
  isLoading,
  isBridgeReady,
  connectionState,
}: ComposerReadinessInput): ComposerReadiness {
  const text = inputText.trim()

  if (!isBridgeReady) {
    return {
      canSend: false,
      reason: 'IDE bridge 正在连接，请稍后',
      text,
    }
  }

  if (connectionState === 'unconfigured') {
    return {
      canSend: false,
      reason: '请先在 Settings 中配置 Provider',
      text,
    }
  }

  if (connectionState === 'error') {
    return {
      canSend: false,
      reason: 'API Key 未设置或未保存，请在 Settings 中重新配置并应用',
      text,
    }
  }

  if (isLoading) {
    return {
      canSend: false,
      reason: '请等待当前响应完成',
      text,
    }
  }

  if (!text) {
    return {
      canSend: false,
      reason: '请输入问题',
      text,
    }
  }

  return {
    canSend: true,
    reason: null,
    text,
  }
}
