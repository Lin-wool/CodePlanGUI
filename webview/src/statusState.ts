type BridgeStatusState = {
  providerName: string
  model: string
  connectionState: 'unconfigured' | 'ready' | 'streaming' | 'error'
  contextFile?: string
}

export function applyBridgeStatus(
  previous: BridgeStatusState,
  next: Omit<BridgeStatusState, 'contextFile'> & Partial<Pick<BridgeStatusState, 'contextFile'>>,
): BridgeStatusState {
  return {
    ...previous,
    ...next,
    contextFile: next.contextFile ?? previous.contextFile ?? '',
  }
}

export function applyContextFile(previous: BridgeStatusState, contextFile: string): BridgeStatusState {
  return {
    ...previous,
    contextFile,
  }
}
