import { PlusOutlined, SettingOutlined, FileTextOutlined } from '@ant-design/icons'
import { Button, Typography } from 'antd'
import { getConnectionStateLabel } from '../composerState'
import { BridgeStatus } from '../types/bridge'

interface Props {
  onNewChat: () => void
  onOpenSettings: () => void
  status: BridgeStatus
  bridgeReady: boolean
}

export function ProviderBar({ onNewChat, onOpenSettings, status, bridgeReady }: Props) {
  const title = status.providerName || 'CodePlanGUI'
  const detail = status.model || (bridgeReady ? 'Select a provider in Settings' : 'Connecting bridge')

  return (
    <div className="provider-bar">
      <div>
        <Typography.Title level={5} className="provider-title">
          {title}
        </Typography.Title>
        <Typography.Text className="provider-meta">
          {detail} · {getConnectionStateLabel(status.connectionState)}
        </Typography.Text>
        {status.contextFile && (
          <Typography.Text className="provider-context" title={status.contextFile}>
            <FileTextOutlined /> {status.contextFile}
          </Typography.Text>
        )}
      </div>
      <div className="provider-actions">
        <Button
          type="text"
          size="small"
          icon={<SettingOutlined />}
          onClick={onOpenSettings}
          title="Open Settings"
          className="provider-action"
        />
        <Button
          type="text"
          size="small"
          icon={<PlusOutlined />}
          onClick={onNewChat}
          title="New Chat"
          className="provider-action"
        />
      </div>
    </div>
  )
}
