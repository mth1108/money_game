// REST 호출 (CLAUDE.md §3 M6). 오류 응답은 {"error": "사유"} 다.
import type { GameMode, GameRecord, HistoryEntry, RoomView, User } from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    throw new ApiError(res.status, data?.error ?? `HTTP ${res.status}`)
  }
  return data as T
}

export const api = {
  enter: (nickname: string) => request<User>('POST', '/api/users', { nickname }),

  listRooms: () => request<RoomView[]>('GET', '/api/rooms'),

  createRoom: (userId: number, mode: GameMode, maxPlayers: number, scenarioId?: number, seedMoney?: string) =>
    request<RoomView>('POST', '/api/rooms', { userId, mode, maxPlayers, scenarioId, seedMoney }),

  room: (roomId: string) => request<RoomView>('GET', `/api/rooms/${roomId}`),

  leave: (roomId: string, userId: number) => request<RoomView>('POST', `/api/rooms/${roomId}/leave`, { userId }),

  result: (resultId: number) => request<GameRecord>('GET', `/api/results/${resultId}`),

  history: (userId: number) => request<HistoryEntry[]>('GET', `/api/users/${userId}/results`),
}
