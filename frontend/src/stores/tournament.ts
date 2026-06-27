import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { tournamentApi, tournamentApiError } from '../api/tournament-api'
import { tournamentBaseUrl } from '../api/tournament-stream'

const SESSION_KEY = 'chess.tournamentIdentity'
const BOTS_ROSTER_KEY = 'chess.tournamentBots'
const BOT_KEY = 'chess.tournamentBot'
const DIRECTOR_KEY = 'chess.tournamentDirector'
const ACTIVE_BOT_KEY = 'chess.tournamentActiveBot'

export interface StoredIdentity {
  id: string
  name: string
  token: string
  isBot: boolean
  /** Id from POST /api/bots — required for director add-at-create */
  registryId?: string
}

function loadStored(key: string): StoredIdentity | null {
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredIdentity
    if (parsed?.id && parsed?.token) return parsed
  } catch {
    /* ignore */
  }
  return null
}

function saveStored(key: string, value: string): void {
  sessionStorage.setItem(key, value)
}

function loadRoster(): StoredIdentity[] {
  try {
    const raw = sessionStorage.getItem(BOTS_ROSTER_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as StoredIdentity[]
      if (Array.isArray(parsed)) return parsed.filter((b) => b?.id && b?.token)
    }
  } catch {
    /* ignore */
  }
  const legacy = loadStored(BOT_KEY)
  if (legacy) {
    sessionStorage.removeItem(BOT_KEY)
    saveRoster([legacy])
    return [legacy]
  }
  return []
}

function saveRoster(bots: StoredIdentity[]): void {
  saveStored(BOTS_ROSTER_KEY, JSON.stringify(bots))
}

function loadActiveBotId(): string | null {
  return sessionStorage.getItem(ACTIVE_BOT_KEY)
}

export interface BulkBotResult {
  ok: string[]
  failed: { name: string; error: string }[]
}

/** Tournament server identities: spectator, director (human), bot roster. */
export const useTournamentStore = defineStore('tournament', () => {
  const spectator = ref<StoredIdentity | null>(loadStored(SESSION_KEY))
  const bots = ref<StoredIdentity[]>(loadRoster())
  const activeBotId = ref<string | null>(
    loadActiveBotId() ?? bots.value[0]?.id ?? null
  )
  const director = ref<StoredIdentity | null>(loadStored(DIRECTOR_KEY))

  const bot = computed(
    () => bots.value.find((b) => b.id === activeBotId.value) ?? bots.value[0] ?? null
  )

  const hasSpectatorToken = computed(() => !!spectator.value?.token)
  const hasBot = computed(() => bots.value.length > 0)
  const hasDirector = computed(() => !!director.value?.token)
  const botCount = computed(() => bots.value.length)

  function setActiveBot(id: string): void {
    if (!bots.value.some((b) => b.id === id)) return
    activeBotId.value = id
    sessionStorage.setItem(ACTIVE_BOT_KEY, id)
  }

  async function ensureSpectatorToken(): Promise<string> {
    if (spectator.value?.token) return spectator.value.token
    const name = `spectator-${Math.random().toString(36).slice(2, 8)}`
    const res = await tournamentApi.registerIdentity(name, false)
    const identity: StoredIdentity = { id: res.id, name, token: res.token, isBot: false }
    spectator.value = identity
    saveStored(SESSION_KEY, JSON.stringify(identity))
    return res.token
  }

  async function registerDirector(name: string): Promise<void> {
    const trimmed = name.trim()
    if (!trimmed) throw new Error('Director name is required.')
    const res = await tournamentApi.registerIdentity(trimmed, false)
    const identity: StoredIdentity = { id: res.id, name: trimmed, token: res.token, isBot: false }
    director.value = identity
    saveStored(DIRECTOR_KEY, JSON.stringify(identity))
  }

  async function registerOneBot(name: string): Promise<StoredIdentity> {
    const trimmed = name.trim()
    if (!trimmed) throw new Error('Bot name is required.')
    const res = await tournamentApi.registerIdentity(trimmed, true)
    let registryId: string | undefined
    try {
      const reg = await tournamentApi.registerPermanentBot({ name: trimmed }, res.token)
      registryId = reg.id
    } catch {
      /* permanent registry optional for self-join */
    }
    return {
      id: res.id,
      name: trimmed,
      token: res.token,
      isBot: true,
      registryId,
    }
  }

  async function registerBot(name: string): Promise<StoredIdentity> {
    const identity = await registerOneBot(name)
    bots.value = [...bots.value, identity]
    saveRoster(bots.value)
    setActiveBot(identity.id)
    return identity
  }

  /** Register several uniquely named bots for solo testing (e.g. maichess-bot-1, maichess-bot-2). */
  async function registerBotFleet(count: number, namePrefix = 'maichess-bot'): Promise<StoredIdentity[]> {
    const n = Math.max(2, Math.min(8, Math.floor(count)))
    const prefix = namePrefix.trim() || 'maichess-bot'
    const created: StoredIdentity[] = []
    for (let i = 0; i < n; i++) {
      const suffix = bots.value.length + created.length + 1
      const name = `${prefix}-${suffix}`
      created.push(await registerOneBot(name))
    }
    bots.value = [...bots.value, ...created]
    saveRoster(bots.value)
    if (created[0]) setActiveBot(created[0].id)
    return created
  }

  function removeBot(id: string): void {
    bots.value = bots.value.filter((b) => b.id !== id)
    saveRoster(bots.value)
    if (activeBotId.value === id) {
      const next = bots.value[0]?.id ?? null
      activeBotId.value = next
      if (next) sessionStorage.setItem(ACTIVE_BOT_KEY, next)
      else sessionStorage.removeItem(ACTIVE_BOT_KEY)
    }
  }

  function clearAllBots(): void {
    bots.value = []
    activeBotId.value = null
    sessionStorage.removeItem(BOTS_ROSTER_KEY)
    sessionStorage.removeItem(ACTIVE_BOT_KEY)
    sessionStorage.removeItem(BOT_KEY)
  }

  function botRegistryId(): string | undefined {
    return bot.value?.registryId
  }

  /** Comma-separated registry ids for tournament creation `bots=` field. */
  function allRegistryIds(): string | undefined {
    const ids = bots.value.map((b) => b.registryId).filter((id): id is string => !!id)
    return ids.length ? ids.join(',') : undefined
  }

  function botsWithRegistry(): StoredIdentity[] {
    return bots.value.filter((b) => !!b.registryId)
  }

  async function joinTournament(tournamentId: string, target?: StoredIdentity): Promise<void> {
    const b = target ?? bot.value
    if (!b?.token) throw new Error('Register a bot first.')
    await tournamentApi.joinTournament(tournamentId, b.token)
  }

  async function joinAllBots(tournamentId: string): Promise<BulkBotResult> {
    const ok: string[] = []
    const failed: { name: string; error: string }[] = []
    for (const b of bots.value) {
      try {
        await tournamentApi.joinTournament(tournamentId, b.token)
        ok.push(b.name)
      } catch (e) {
        const msg = tournamentApiError(e)
        if (/already|duplicate|joined/i.test(msg)) ok.push(b.name)
        else failed.push({ name: b.name, error: msg })
      }
    }
    return { ok, failed }
  }

  async function addBotAsDirector(tournamentId: string, target: StoredIdentity): Promise<void> {
    if (!director.value?.token) throw new Error('Register as director first.')
    if (!target.registryId) throw new Error(`${target.name} has no registry id — re-register the bot.`)
    await tournamentApi.addParticipant(tournamentId, target.registryId, director.value.token)
  }

  async function addAllBotsAsDirector(tournamentId: string): Promise<BulkBotResult> {
    const ok: string[] = []
    const failed: { name: string; error: string }[] = []
    for (const b of botsWithRegistry()) {
      try {
        await addBotAsDirector(tournamentId, b)
        ok.push(b.name)
      } catch (e) {
        const msg = tournamentApiError(e)
        if (/already|duplicate|present/i.test(msg)) ok.push(b.name)
        else failed.push({ name: b.name, error: msg })
      }
    }
    for (const b of bots.value.filter((x) => !x.registryId)) {
      failed.push({ name: b.name, error: 'Missing registry id — remove and re-register.' })
    }
    return { ok, failed }
  }

  function isDirectorOf(createdBy: string | undefined): boolean {
    return !!director.value?.id && createdBy === director.value.id
  }

  /** Shell snippet to run the local tournament-bot for a joined tournament. */
  function botRunCommand(tournamentId: string, target?: StoredIdentity): string {
    const b = target ?? bot.value
    const token = b?.token ?? '<TOURNAMENT_TOKEN>'
    const botId = b?.id ?? '<TOURNAMENT_BOT_ID>'
    return [
      `export TOURNAMENT_API_URL=${tournamentBaseUrl()}`,
      `export TOURNAMENT_TOKEN='${token}'`,
      `export TOURNAMENT_BOT_ID='${botId}'`,
      `export TOURNAMENT_ID=${tournamentId}`,
      `sbt "TournamentBot/runMain chess.tournament.TournamentBotMain"`,
    ].join('\n')
  }

  function clearBot(): void {
    clearAllBots()
  }

  function clearDirector(): void {
    director.value = null
    sessionStorage.removeItem(DIRECTOR_KEY)
  }

  return {
    spectator,
    bots,
    bot,
    activeBotId,
    director,
    hasSpectatorToken,
    hasBot,
    hasDirector,
    botCount,
    ensureSpectatorToken,
    registerDirector,
    registerBot,
    registerBotFleet,
    removeBot,
    clearAllBots,
    setActiveBot,
    joinTournament,
    joinAllBots,
    addBotAsDirector,
    addAllBotsAsDirector,
    isDirectorOf,
    botRunCommand,
    botRegistryId,
    allRegistryIds,
    botsWithRegistry,
    clearBot,
    clearDirector,
  }
})
