// REST 호출 (CLAUDE.md §3 M6). 오류 응답은 {"error": "사유"} 다.
// axios 를 쓴다 (2026-10-03). 금액은 서버가 문자열로 보내므로 JSON 파싱에서 정밀도가 깨지지 않는다 (§1.5).
import axios, { type Method } from 'axios'
import type { GameMode, GameRecord, HistoryEntry, RoomOptions, RoomView, User } from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** 개발 서버·preview 가 /api 를 백엔드로 프록시한다 — 같은 출처라 baseURL 이 필요 없다 */
const http = axios.create()

/** 화면은 ApiError 하나만 다룬다. 서버 응답이 없으면(연결 실패) status 0 이다 */
async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  try {
    const res = await http.request<T>({ method, url: path, data: body })
    return res.data
  } catch (err) {
    if (axios.isAxiosError<{ error?: string }>(err)) {
      if (err.response) {
        throw new ApiError(err.response.status, err.response.data?.error ?? `HTTP ${err.response.status}`)
      }
      throw new ApiError(0, '서버에 연결할 수 없습니다')
    }
    throw err
  }
}

export const api = {
  enter: (nickname: string) => request<User>('POST', '/api/users', { nickname }),

  listRooms: () => request<RoomView[]>('GET', '/api/rooms'),

  roomOptions: () => request<RoomOptions>('GET', '/api/rooms/options'),

  createRoom: (
    userId: number,
    mode: GameMode,
    maxPlayers: number,
    scenarioId?: number,
    seedMoney?: string,
    bots?: number,
    ticks?: number,
  ) => request<RoomView>('POST', '/api/rooms', { userId, mode, maxPlayers, scenarioId, seedMoney, bots, ticks }),

  room: (roomId: string) => request<RoomView>('GET', `/api/rooms/${roomId}`),

  leave: (roomId: string, userId: number) => request<RoomView>('POST', `/api/rooms/${roomId}/leave`, { userId }),

  result: (resultId: number) => request<GameRecord>('GET', `/api/results/${resultId}`),

  history: (userId: number) => request<HistoryEntry[]>('GET', `/api/users/${userId}/results`),
}
