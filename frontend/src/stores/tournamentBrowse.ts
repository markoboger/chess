import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import {
  tournamentApi,
  tournamentApiError,
  type TournamentInfo,
  type TournamentList,
  type TournamentResult,
  type RoundPairing,
} from '../api/tournament-api'
import { streamNdjson } from '../api/tournament-stream'
import {
  pairingsToGameRows,
  formatGameStateResult,
  isGameLiveStatus,
  type TournamentGameRow,
} from '../utils/tournament-game'
import { useGameStore } from './game'
import { useTournamentStore } from './tournament'

export type { TournamentGameRow }
export type DetailTab = 'tournament' | 'moves'
export type GamesScope = 'all' | 'round'

export const useTournamentBrowseStore = defineStore('tournamentBrowse', () => {
  const gameStore = useGameStore()
  const tournamentStore = useTournamentStore()

  const active = ref(false)
  const tournaments = ref<TournamentList>({ created: [], started: [], finished: [] })
  const selectedId = ref<string | null>(null)
  const detail = ref<TournamentInfo | null>(null)
  const standings = ref<TournamentResult[]>([])
  const pairings = ref<RoundPairing[]>([])
  const selectedRound = ref(1)
  const selectedGameId = ref<string | null>(null)
  const playerFilterId = ref<string | null>(null)
  const detailTab = ref<DetailTab>('tournament')
  const gamesScope = ref<GamesScope>('all')
  const roundCache = ref<Record<number, RoundPairing[]>>({})
  const loadingAllRounds = ref(false)
  const selectedGameUci = ref('')
  const selectedGameResult = ref<string | null>(null)
  const gameMetaCache = ref<
    Record<string, { status?: string; outcomeLabel?: string; moves?: string }>
  >({})

  const loadingList = ref(false)
  const loadingDetail = ref(false)
  const loadingRound = ref(false)
  const loadingBoard = ref(false)
  const liveStream = ref(true)
  const statusText = ref('Select a tournament.')
  const localError = ref<string | null>(null)

  const botNameInput = ref('maichess-bot')
  const directorNameInput = ref('maichess-director')
  const botMessage = ref<string | null>(null)
  const directorMessage = ref<string | null>(null)
  const registering = ref(false)
  const registeringDirector = ref(false)
  const joining = ref(false)
  const registeringFleet = ref(false)
  const bulkBotAction = ref(false)
  const creating = ref(false)
  const starting = ref(false)

  const createName = ref('Maichess Swiss')
  const createRounds = ref(5)
  const createClockMinutes = ref(5)
  const createClockIncrement = ref(3)
  const createFormat = ref('swiss')
  const createAddAllBots = ref(true)
  const showCreateForm = ref(false)
  const botFleetCount = ref(2)

  const FORMAT_OPTIONS = [
    { id: 'swiss', label: 'Swiss' },
    { id: 'singleElimination', label: 'Single elimination' },
    { id: 'league', label: 'League' },
    { id: 'randomKnockout', label: 'Random knockout' },
  ] as const

  let tournamentStreamAbort: AbortController | null = null

  const totalCount = computed(
    () => tournaments.value.created.length + tournaments.value.started.length + tournaments.value.finished.length
  )

  const roundNumbers = computed(() => {
    const n = detail.value?.nbRounds ?? 0
    return Array.from({ length: n }, (_, i) => i + 1)
  })

  const currentTournamentRound = computed(() => {
    const r = detail.value?.round ?? 0
    return r > 0 ? r : 1
  })

  const allGameRows = computed((): TournamentGameRow[] => {
    const status = detail.value?.status
    const current = currentTournamentRound.value
    const rows: TournamentGameRow[] = []
    for (const [key, ps] of Object.entries(roundCache.value)) {
      const round = Number(key)
      if (!Number.isFinite(round)) continue
      rows.push(...pairingsToGameRows(ps, round, status, current))
    }
    return rows.sort((a, b) => a.round - b.round || a.gameId.localeCompare(b.gameId))
  })

  const gameRows = computed((): TournamentGameRow[] => {
    return pairingsToGameRows(
      pairings.value,
      selectedRound.value,
      detail.value?.status,
      currentTournamentRound.value
    )
  })

  const displayGameRows = computed((): TournamentGameRow[] => {
    const base = gamesScope.value === 'all' ? allGameRows.value : gameRows.value
    return base.map((g) => {
      const meta = gameMetaCache.value[g.gameId]
      if (!meta) return g
      return {
        ...g,
        moves: meta.moves ?? g.moves,
        moveCount: (meta.moves ?? g.moves).trim().split(/\s+/).filter(Boolean).length,
        outcomeLabel: meta.outcomeLabel ?? g.outcomeLabel,
        status: meta.status ?? g.status,
        isLive: meta.status ? isGameLiveStatus(meta.status) : g.isLive,
        isFinished: meta.status ? !isGameLiveStatus(meta.status) : g.isFinished,
      }
    })
  })

  const filteredGames = computed(() => {
    if (!playerFilterId.value) return displayGameRows.value
    const id = playerFilterId.value
    return displayGameRows.value.filter((g) => g.white.id === id || g.black.id === id)
  })

  const liveGames = computed(() => filteredGames.value.filter((g) => g.isLive))
  const finishedGames = computed(() => filteredGames.value.filter((g) => g.isFinished))

  const selectedGame = computed(() => {
    const pool = displayGameRows.value
    return pool.find((g) => g.gameId === selectedGameId.value) ?? null
  })

  const boardGameLoaded = computed(
    () => !!(gameStore.tournamentWhiteName || gameStore.tournamentWatchLabel)
  )

  const isOurTournament = computed(
    () => !!detail.value && tournamentStore.isDirectorOf(detail.value.createdBy)
  )

  const canStartTournament = computed(
    () =>
      detail.value?.status === 'created' &&
      isOurTournament.value &&
      (detail.value.nbPlayers ?? 0) >= 2
  )

  const startBlockedReason = computed(() => {
    if (!detail.value || detail.value.status !== 'created') return null
    if (!isOurTournament.value) return 'Only the director who created this tournament can start it.'
    if ((detail.value.nbPlayers ?? 0) < 2) return `Need at least 2 bots (currently ${detail.value.nbPlayers ?? 0}).`
    return null
  })

  async function loadAllRoundsCache(tournamentId: string) {
    if (!detail.value) return
    loadingAllRounds.value = true
    const nb = detail.value.nbRounds
    const maxRound = Math.max(currentTournamentRound.value, selectedRound.value, 1)
    const cache: Record<number, RoundPairing[]> = {}
    for (let r = 1; r <= Math.min(maxRound, nb); r++) {
      try {
        const data = await tournamentApi.getRoundPairings(tournamentId, r)
        cache[r] = data.pairings ?? []
      } catch {
        cache[r] = []
      }
    }
    roundCache.value = cache
    loadingAllRounds.value = false
    void enrichFinishedGameMeta(tournamentId)
  }

  async function enrichFinishedGameMeta(tournamentId: string) {
    const finished = allGameRows.value.filter((g) => g.isFinished).slice(0, 32)
    await Promise.all(
      finished.map(async (g) => {
        if (gameMetaCache.value[g.gameId]?.outcomeLabel) return
        try {
          const state = await tournamentApi.getGame(tournamentId, g.gameId)
          gameMetaCache.value[g.gameId] = {
            status: state.status,
            outcomeLabel: formatGameStateResult(state),
            moves: state.moves,
          }
        } catch {
          /* ignore */
        }
      })
    )
  }

  function cacheGameMeta(
    gameId: string,
    meta: { status?: string; outcomeLabel?: string; moves?: string }
  ) {
    gameMetaCache.value = { ...gameMetaCache.value, [gameId]: meta }
  }

  function stopTournamentStream() {
    if (tournamentStreamAbort) {
      tournamentStreamAbort.abort()
      tournamentStreamAbort = null
    }
  }

  async function refreshSelectedTournament() {
    if (!selectedId.value) return
    try {
      detail.value = await tournamentApi.getTournament(selectedId.value)
      standings.value = detail.value.standing?.players ?? []
      if (detail.value.round && detail.value.round > 0) {
        selectedRound.value = detail.value.round
        await loadRound(selectedId.value, selectedRound.value)
      }
      await loadAllRoundsCache(selectedId.value)
      statusText.value = `${detail.value.fullName} — round ${selectedRound.value}/${detail.value.nbRounds} (${detail.value.status})`
    } catch {
      /* background refresh */
    }
  }

  async function startTournamentStream(tournamentId: string) {
    stopTournamentStream()
    const abort = new AbortController()
    tournamentStreamAbort = abort
    try {
      const token = await tournamentStore.ensureSpectatorToken()
      await streamNdjson(`/api/tournament/${tournamentId}/stream`, {
        token,
        signal: abort.signal,
        onLine: (line) => {
          const typ = String(line.type ?? '')
          if (typ === 'heartbeat') return
          if (typ === 'roundStarted' || typ === 'roundFinished' || typ === 'tournamentFinished' || typ === 'gameStart') {
            void refreshSelectedTournament()
          }
        },
      })
    } catch (e) {
      if ((e as Error).name !== 'AbortError' && active.value) {
        localError.value = 'Live tournament stream disconnected.'
      }
    }
  }

  watch(liveStream, (on) => {
    if (on && active.value && selectedId.value && detail.value?.status === 'started') {
      void startTournamentStream(selectedId.value)
    } else {
      stopTournamentStream()
    }
  })

  async function loadList() {
    loadingList.value = true
    localError.value = null
    try {
      tournaments.value = await tournamentApi.listTournaments()
      statusText.value =
        totalCount.value === 0
          ? 'No tournaments on the server.'
          : `${totalCount.value} tournament(s).`
    } catch {
      localError.value =
        'Could not reach the tournament server. Connect VPN or set VITE_TOURNAMENT_API_URL.'
      statusText.value = 'Connection failed.'
    } finally {
      loadingList.value = false
    }
  }

  async function selectTournament(id: string) {
    selectedId.value = id
    playerFilterId.value = null
    selectedGameId.value = null
    loadingDetail.value = true
    localError.value = null
    statusText.value = 'Loading…'
    try {
      detail.value = await tournamentApi.getTournament(id)
      standings.value = detail.value.standing?.players ?? []
      const startRound = detail.value.round && detail.value.round > 0 ? detail.value.round : 1
      selectedRound.value = Math.min(startRound, detail.value.nbRounds)
      await loadRound(id, selectedRound.value)
      statusText.value = `${detail.value.fullName} — round ${selectedRound.value}/${detail.value.nbRounds} (${detail.value.status})`
      await loadAllRoundsCache(id)
      await autoSelectFirstGame()
      if (liveStream.value && detail.value.status === 'started') {
        void startTournamentStream(id)
      }
    } catch {
      detail.value = null
      standings.value = []
      pairings.value = []
      localError.value = 'Failed to load tournament.'
    } finally {
      loadingDetail.value = false
    }
  }

  async function loadRound(tournamentId: string, round: number) {
    loadingRound.value = true
    try {
      const data = await tournamentApi.getRoundPairings(tournamentId, round)
      pairings.value = data.pairings ?? []
      roundCache.value = { ...roundCache.value, [round]: pairings.value }
    } catch {
      pairings.value = []
      localError.value = `Could not load round ${round}.`
    } finally {
      loadingRound.value = false
    }
  }

  function setGamesScope(scope: GamesScope) {
    gamesScope.value = scope
  }

  function setDetailTab(tab: DetailTab) {
    detailTab.value = tab
  }

  function selectRound(r: number) {
    selectedRound.value = r
    selectedGameId.value = null
    if (selectedId.value) {
      void loadRound(selectedId.value, r).then(() => autoSelectFirstGame())
    }
  }

  async function autoSelectFirstGame() {
    const games = displayGameRows.value
    if (games.length === 0) return
    const live = games.find((g) => g.isLive)
    const pick = live ?? games[games.length - 1]
    await selectGame(pick)
  }

  function togglePlayerFilter(botId: string) {
    if (playerFilterId.value === botId) {
      playerFilterId.value = null
      selectedGameId.value = null
      return
    }
    playerFilterId.value = botId
    selectedGameId.value = null
    const games = filteredGames.value
    const live = games.find((g) => g.isLive)
    const pick = live ?? games[0]
    if (pick) void selectGame(pick)
  }

  async function showGameOnBoard(g: TournamentGameRow): Promise<boolean> {
    if (!selectedId.value) return false
    loadingBoard.value = true
    localError.value = null
    try {
      let moves = g.moves
      let live = g.isLive
      try {
        const state = await tournamentApi.getGame(selectedId.value, g.gameId)
        moves = state.moves ?? moves
        live = isGameLiveStatus(state.status)
        selectedGameUci.value = moves
        selectedGameResult.value = formatGameStateResult(state)
        cacheGameMeta(g.gameId, {
          status: state.status,
          outcomeLabel: formatGameStateResult(state),
          moves,
        })
      } catch {
        selectedGameUci.value = moves
        selectedGameResult.value = g.outcomeLabel !== '…' ? g.outcomeLabel : null
      }

      if (live) {
        let token: string | null = null
        try {
          token = await tournamentStore.ensureSpectatorToken()
        } catch {
          token = null
        }
        return await gameStore.startTournamentLiveWatch(selectedId.value, g.gameId, {
          whiteName: g.white.name,
          blackName: g.black.name,
          startPosition: detail.value?.startPosition,
          tournamentName: detail.value?.fullName,
          token,
        })
      }
      if (!moves.trim()) {
        localError.value = 'No moves recorded for this game.'
        return false
      }
      return gameStore.replayTournamentUci(moves, {
        whiteName: g.white.name,
        blackName: g.black.name,
        startPosition: detail.value?.startPosition,
      })
    } catch {
      localError.value = 'Could not load game on the board.'
      return false
    } finally {
      loadingBoard.value = false
    }
  }

  async function selectGame(g: TournamentGameRow) {
    selectedGameId.value = g.gameId
    if (gamesScope.value === 'round' && g.round !== selectedRound.value && selectedId.value) {
      selectedRound.value = g.round
      await loadRound(selectedId.value, g.round)
    }
    const ok = await showGameOnBoard(g)
    if (ok) {
      statusText.value = g.isLive
        ? `Live: ${g.white.name} vs ${g.black.name} (R${g.round})`
        : `Replay: ${g.white.name} vs ${g.black.name} (R${g.round})`
    }
  }

  async function registerDirector() {
    registeringDirector.value = true
    directorMessage.value = null
    localError.value = null
    try {
      await tournamentStore.registerDirector(directorNameInput.value)
      directorMessage.value = `Director: ${tournamentStore.director?.name}`
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      registeringDirector.value = false
    }
  }

  async function createTournament() {
    if (!tournamentStore.director?.token) {
      localError.value = 'Register as director first.'
      return
    }
    creating.value = true
    localError.value = null
    directorMessage.value = null
    try {
      const bots =
        createAddAllBots.value && tournamentStore.allRegistryIds()
          ? tournamentStore.allRegistryIds()
          : undefined
      const created = await tournamentApi.createTournament(
        {
          name: createName.value.trim(),
          nbRounds: createRounds.value,
          clockLimit: createClockMinutes.value * 60,
          clockIncrement: createClockIncrement.value,
          format: createFormat.value,
          bots,
        },
        tournamentStore.director.token
      )
      await loadList()
      await selectTournament(created.id)
      directorMessage.value = `Created "${created.fullName}" (${created.id}).`
      showCreateForm.value = false
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      creating.value = false
    }
  }

  async function startTournament() {
    if (!selectedId.value || !tournamentStore.director?.token) return
    starting.value = true
    localError.value = null
    directorMessage.value = null
    try {
      await tournamentApi.startTournament(selectedId.value, tournamentStore.director.token)
      directorMessage.value = 'Tournament started!'
      await loadList()
      await selectTournament(selectedId.value)
      if (liveStream.value) void startTournamentStream(selectedId.value)
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      starting.value = false
    }
  }

  async function registerBot() {
    registering.value = true
    botMessage.value = null
    localError.value = null
    try {
      const identity = await tournamentStore.registerBot(botNameInput.value)
      botMessage.value = `Bot registered: ${identity.name} (${tournamentStore.botCount} total)`
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      registering.value = false
    }
  }

  async function registerBotFleet() {
    registeringFleet.value = true
    botMessage.value = null
    localError.value = null
    try {
      const created = await tournamentStore.registerBotFleet(botFleetCount.value, botNameInput.value)
      botMessage.value = `Registered ${created.length} bots: ${created.map((b) => b.name).join(', ')}`
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      registeringFleet.value = false
    }
  }

  function formatBulkResult(label: string, result: { ok: string[]; failed: { name: string; error: string }[] }): string {
    const parts = [`${label}: ${result.ok.length} ok`]
    if (result.ok.length) parts.push(result.ok.join(', '))
    if (result.failed.length) {
      parts.push(`${result.failed.length} failed: ${result.failed.map((f) => `${f.name} (${f.error})`).join('; ')}`)
    }
    return parts.join(' — ')
  }

  async function joinAsBot(botId?: string) {
    if (!selectedId.value) return
    const target = botId ? tournamentStore.bots.find((b) => b.id === botId) : tournamentStore.bot
    if (!target) return
    joining.value = true
    botMessage.value = null
    localError.value = null
    try {
      await tournamentStore.joinTournament(selectedId.value, target)
      botMessage.value = `${target.name} joined. Run the bot locally to play (see commands below).`
      await loadList()
      await selectTournament(selectedId.value)
    } catch (e) {
      botMessage.value = tournamentApiError(e)
    } finally {
      joining.value = false
    }
  }

  async function joinAllBots() {
    if (!selectedId.value || !tournamentStore.hasBot) return
    bulkBotAction.value = true
    botMessage.value = null
    localError.value = null
    try {
      const result = await tournamentStore.joinAllBots(selectedId.value)
      botMessage.value = formatBulkResult('Joined', result)
      await loadList()
      await selectTournament(selectedId.value)
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      bulkBotAction.value = false
    }
  }

  async function addAllBotsAsDirector() {
    if (!selectedId.value || !tournamentStore.hasDirector) return
    bulkBotAction.value = true
    botMessage.value = null
    localError.value = null
    try {
      const result = await tournamentStore.addAllBotsAsDirector(selectedId.value)
      botMessage.value = formatBulkResult('Added', result)
      await loadList()
      await selectTournament(selectedId.value)
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      bulkBotAction.value = false
    }
  }

  /** Register bots (if needed) and join the selected tournament with all of them. */
  async function setupTestParticipation() {
    if (!selectedId.value) {
      localError.value = 'Select a tournament first.'
      return
    }
    bulkBotAction.value = true
    botMessage.value = null
    localError.value = null
    try {
      if (tournamentStore.botCount < 2) {
        await tournamentStore.registerBotFleet(Math.max(2, botFleetCount.value), botNameInput.value)
      }
      if (isOurTournament.value && detail.value?.status === 'created') {
        const result = await tournamentStore.addAllBotsAsDirector(selectedId.value)
        botMessage.value = formatBulkResult('Test setup — added', result)
      } else {
        const result = await tournamentStore.joinAllBots(selectedId.value)
        botMessage.value = formatBulkResult('Test setup — joined', result)
      }
      await loadList()
      await selectTournament(selectedId.value)
    } catch (e) {
      localError.value = tournamentApiError(e)
    } finally {
      bulkBotAction.value = false
    }
  }

  /** Register bot (if needed) and join the selected tournament. */
  async function participateInSelected() {
    if (!selectedId.value) {
      localError.value = 'Select a tournament first.'
      return
    }
    if (!tournamentStore.hasBot) {
      await registerBot()
      if (!tournamentStore.hasBot) return
    }
    await joinAsBot()
  }

  async function copyBotRunCommand(botId?: string) {
    if (!selectedId.value) return
    const target = botId ? tournamentStore.bots.find((b) => b.id === botId) : tournamentStore.bot
    if (!target) return
    const cmd = tournamentStore.botRunCommand(selectedId.value, target)
    await navigator.clipboard.writeText(cmd)
    botMessage.value = `Run command copied for ${target.name}.`
  }

  function activate() {
    active.value = true
    void loadList()
  }

  function deactivate() {
    active.value = false
    stopTournamentStream()
  }

  function reset() {
    selectedId.value = null
    detail.value = null
    standings.value = []
    pairings.value = []
    roundCache.value = {}
    gameMetaCache.value = {}
    playerFilterId.value = null
    selectedGameId.value = null
    selectedGameUci.value = ''
    selectedGameResult.value = null
    detailTab.value = 'tournament'
    gamesScope.value = 'all'
    localError.value = null
    botMessage.value = null
    stopTournamentStream()
  }

  /** Deep-link: select tournament and open a game on the board. */
  async function openGameFromUrl(tournamentId: string, gameId: string) {
    active.value = true
    await loadList()
    await selectTournament(tournamentId)
    const row = displayGameRows.value.find((g) => g.gameId === gameId)
    if (row) {
      await selectGame(row)
      return
    }
    try {
      const state = await tournamentApi.getGame(tournamentId, gameId)
      const live = isGameLiveStatus(state.status)
      const g: TournamentGameRow = {
        gameId,
        round: state.round ?? selectedRound.value,
        white: state.white,
        black: state.black,
        moves: state.moves ?? '',
        outcomeLabel: formatGameStateResult(state),
        moveCount: (state.moves ?? '').trim().split(/\s+/).filter(Boolean).length,
        isLive: live,
        isFinished: !live,
        status: state.status,
      }
      selectedGameId.value = gameId
      await showGameOnBoard(g)
    } catch {
      localError.value = 'Could not open linked game.'
    }
  }

  return {
    active,
    tournaments,
    selectedId,
    detail,
    standings,
    pairings,
    selectedRound,
    selectedGameId,
    playerFilterId,
    loadingList,
    loadingDetail,
    loadingRound,
    loadingBoard,
    liveStream,
    statusText,
    localError,
    botNameInput,
    directorNameInput,
    botMessage,
    directorMessage,
    registering,
    registeringDirector,
    registeringFleet,
    bulkBotAction,
    creating,
    starting,
    createName,
    createRounds,
    createClockMinutes,
    createClockIncrement,
    createFormat,
    createAddAllBots,
    showCreateForm,
    botFleetCount,
    FORMAT_OPTIONS,
    isOurTournament,
    canStartTournament,
    startBlockedReason,
    totalCount,
    roundNumbers,
    currentTournamentRound,
    gameRows,
    allGameRows,
    displayGameRows,
    filteredGames,
    liveGames,
    finishedGames,
    selectedGame,
    detailTab,
    gamesScope,
    loadingAllRounds,
    selectedGameUci,
    selectedGameResult,
    boardGameLoaded,
    loadList,
    selectTournament,
    selectRound,
    setDetailTab,
    setGamesScope,
    togglePlayerFilter,
    selectGame,
    showGameOnBoard,
    registerBot,
    registerDirector,
    createTournament,
    startTournament,
    joining,
    joinAsBot,
    registerBotFleet,
    joinAllBots,
    addAllBotsAsDirector,
    setupTestParticipation,
    participateInSelected,
    copyBotRunCommand,
    activate,
    deactivate,
    reset,
    openGameFromUrl,
    stopTournamentStream,
  }
})
