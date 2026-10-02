// WebSocket 연결 (CLAUDE.md §3 M7). 개발 서버가 /ws 를 백엔드로 프록시한다.
import type { ClientMessage, ServerMessage } from './messages'

export type SocketStatus = '연결 중' | '연결됨' | '끊김'

export interface GameSocket {
  send: (msg: ClientMessage) => void
  close: () => void
}

export function connectGame(handlers: {
  onOpen: () => void
  onMessage: (msg: ServerMessage, raw: string) => void
  onStatus: (status: SocketStatus) => void
}): GameSocket {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  const ws = new WebSocket(`${scheme}://${location.host}/ws`)
  handlers.onStatus('연결 중')

  ws.onopen = () => {
    handlers.onStatus('연결됨')
    handlers.onOpen()
  }
  ws.onclose = () => handlers.onStatus('끊김')
  ws.onmessage = (e) => handlers.onMessage(JSON.parse(e.data as string) as ServerMessage, e.data as string)

  return {
    send: (msg) => {
      if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg))
    },
    close: () => ws.close(),
  }
}
