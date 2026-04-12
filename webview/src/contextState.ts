export function getContextToggleMeta(includeContext: boolean, contextLabel: string) {
  if (!includeContext) {
    return {
      label: 'context off',
      title: '不附加文件上下文',
    }
  }

  if (!contextLabel) {
    return {
      label: 'no open file',
      title: '当前没有可附加的文件上下文',
    }
  }

  return {
    label: 'context on',
    title: contextLabel,
  }
}
