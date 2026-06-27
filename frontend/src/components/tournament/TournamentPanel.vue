<template>
  <div class="tournament-panel">
    <div class="panel-header">
      <span class="panel-title">
        <Trophy :size="16" :stroke-width="2" class="panel-title-ico" aria-hidden="true" />
        Tournaments
      </span>
      <button type="button" class="icon-btn" :disabled="browse.loadingList" title="Refresh" @click="browse.loadList()">
        ↻
      </button>
    </div>

    <div v-if="browse.localError" class="panel-error">{{ browse.localError }}</div>

    <div class="panel-scroll">
      <!-- Tournament list (pick one first) -->
      <section class="panel-section">
        <div class="section-label">Open &amp; live</div>
        <ul class="item-list compact-list">
          <li
            v-for="t in [...browse.tournaments.created, ...browse.tournaments.started]"
            :key="t.id"
            :class="{ selected: browse.selectedId === t.id, live: t.status === 'started' }"
            @click="browse.selectTournament(t.id)"
          >
            <span class="item-title">{{ t.fullName }}</span>
            <span class="item-meta">
              <span v-if="t.status === 'started'" class="live-pill">LIVE R{{ t.round }}</span>
              <span v-else>created</span>
              · {{ t.nbPlayers }} bots
            </span>
          </li>
          <li v-if="browse.tournaments.created.length + browse.tournaments.started.length === 0" class="empty">
            No open tournaments
          </li>
        </ul>

        <div v-if="browse.tournaments.finished.length" class="section-label finished-label">Finished</div>
        <ul v-if="browse.tournaments.finished.length" class="item-list finished-list compact-list">
          <li
            v-for="t in browse.tournaments.finished.slice(0, 8)"
            :key="t.id"
            :class="{ selected: browse.selectedId === t.id }"
            @click="browse.selectTournament(t.id)"
          >
            <span class="item-title">{{ t.fullName }}</span>
            <span class="item-meta">{{ t.winner?.name ?? '?' }} won</span>
          </li>
        </ul>
      </section>

      <!-- Selected tournament: games first -->
      <section v-if="browse.detail" ref="detailSectionEl" class="panel-section detail-section">
        <div class="detail-head">
          <h3 class="detail-name">{{ browse.detail.fullName }}</h3>
          <span
            class="status-chip"
            :class="browse.detail.status"
            :title="`Tournament status: ${browse.detail.status}`"
          >
            {{ browse.detail.status }}
          </span>
        </div>

        <label v-if="browse.detail.status === 'started'" class="live-check">
          <input v-model="browse.liveStream" type="checkbox" />
          Live updates
        </label>

        <div v-if="browse.detail.status === 'created'" class="start-box">
          <div class="start-meta">{{ browse.detail.nbPlayers }} bot(s) registered</div>
          <button
            v-if="browse.isOurTournament && tournamentStore.botsWithRegistry().length"
            type="button"
            class="btn-sm full-width"
            :disabled="browse.bulkBotAction"
            @click="browse.addAllBotsAsDirector()"
          >
            {{ browse.bulkBotAction ? 'Adding…' : 'Add all my bots' }}
          </button>
          <button
            v-if="browse.isOurTournament"
            type="button"
            class="btn-sm btn-start full-width"
            :disabled="!browse.canStartTournament || browse.starting"
            @click="browse.startTournament()"
          >
            {{ browse.starting ? 'Starting…' : 'Start tournament' }}
          </button>
          <p v-if="browse.startBlockedReason && browse.isOurTournament" class="hint-msg warn">
            {{ browse.startBlockedReason }}
          </p>
          <p v-else-if="!browse.isOurTournament" class="hint-msg">
            Waiting for the director to start (need 2+ bots, then Start).
          </p>
        </div>

        <div class="detail-tabs">
          <button
            type="button"
            class="detail-tab"
            :class="{ active: browse.detailTab === 'tournament' }"
            @click="browse.setDetailTab('tournament')"
          >
            Tournament
          </button>
          <button
            type="button"
            class="detail-tab"
            :class="{ active: browse.detailTab === 'moves' }"
            :disabled="!browse.selectedGame"
            @click="browse.setDetailTab('moves')"
          >
            Moves
          </button>
        </div>

        <TournamentMovesTab v-if="browse.detailTab === 'moves'" />

        <template v-else>
          <div class="scope-row">
            <label class="scope-opt">
              <input
                type="radio"
                name="games-scope"
                :checked="browse.gamesScope === 'all'"
                @change="browse.setGamesScope('all')"
              />
              All rounds
            </label>
            <label class="scope-opt">
              <input
                type="radio"
                name="games-scope"
                :checked="browse.gamesScope === 'round'"
                @change="browse.setGamesScope('round')"
              />
              One round
            </label>
            <span v-if="browse.loadingAllRounds || browse.loadingRound" class="loading-tag">loading…</span>
          </div>

          <div v-if="browse.gamesScope === 'round'" class="round-row">
            <button
              v-for="r in browse.roundNumbers"
              :key="r"
              type="button"
              class="round-btn"
              :class="{ active: browse.selectedRound === r }"
              @click="browse.selectRound(r)"
            >
              {{ r }}
            </button>
          </div>

          <p
            v-if="browse.gamesScope === 'round' && !browse.loadingRound && browse.gameRows.length === 0"
            class="hint-msg warn"
          >
            No pairings in round {{ browse.selectedRound }}.
            <template v-if="browse.selectedRound === browse.currentTournamentRound && browse.detail.status === 'started'">
              The tournament may be stalled (e.g. after a timeout) — check earlier rounds below.
            </template>
          </p>

          <div class="games-block">
            <div class="section-label">Live · {{ browse.liveGames.length }}</div>
            <ul class="item-list games-list">
              <li
                v-for="g in browse.liveGames"
                :key="g.gameId"
                class="live"
                :class="{ selected: browse.selectedGameId === g.gameId }"
                @click="browse.selectGame(g)"
              >
                <span class="item-title">{{ g.white.name }} vs {{ g.black.name }}</span>
                <span class="item-meta">
                  <span class="live-pill">live</span>
                  R{{ g.round }}
                  <template v-if="g.moveCount"> · {{ g.moveCount }} plies</template>
                </span>
              </li>
              <li v-if="browse.liveGames.length === 0" class="empty">No live games</li>
            </ul>
          </div>

          <div class="games-block">
            <div class="section-label">Finished · {{ browse.finishedGames.length }}</div>
            <ul class="item-list games-list finished-games-list">
              <li
                v-for="g in browse.finishedGames"
                :key="g.gameId"
                :class="{ selected: browse.selectedGameId === g.gameId }"
                @click="browse.selectGame(g)"
              >
                <span class="item-title">{{ g.white.name }} vs {{ g.black.name }}</span>
                <span class="item-meta">
                  R{{ g.round }} · {{ g.outcomeLabel }}
                  <template v-if="g.moveCount"> · {{ g.moveCount }} plies</template>
                </span>
              </li>
              <li v-if="browse.finishedGames.length === 0" class="empty">No finished games yet</li>
            </ul>
          </div>

          <p class="games-hint">Click any game to load it on the board · open the Moves tab for SAN/UCI</p>

          <div class="section-label row-label">
            Standings
            <button v-if="browse.playerFilterId" type="button" class="link-sm" @click="browse.playerFilterId = null">
              Clear filter
            </button>
          </div>
          <ul class="standings-list">
            <li
              v-for="row in browse.standings"
              :key="row.bot.id"
              :class="{ selected: browse.playerFilterId === row.bot.id }"
              @click="browse.togglePlayerFilter(row.bot.id)"
            >
              <span class="rank">#{{ row.rank }}</span>
              <span class="name">{{ row.bot.name }}</span>
              <span class="pts">{{ row.points }}</span>
            </li>
          </ul>
        </template>
      </section>

      <!-- Setup (collapsed while browsing a tournament) -->
      <details class="setup-details" :open="setupOpen">
        <summary class="setup-summary">Setup · director &amp; bots</summary>

        <section class="panel-section action-section nested-section">
      <div class="section-label">Organise</div>
      <div v-if="!tournamentStore.hasDirector" class="action-row">
        <input
          v-model="browse.directorNameInput"
          type="text"
          class="field-input"
          placeholder="Director name"
        />
        <button type="button" class="btn-sm" :disabled="browse.registeringDirector" @click="browse.registerDirector()">
          Register
        </button>
      </div>
      <div v-else class="identity-row">
        <span class="identity-tag">Director · {{ tournamentStore.director?.name }}</span>
        <button type="button" class="link-sm" @click="tournamentStore.clearDirector()">Change</button>
      </div>

      <button
        v-if="tournamentStore.hasDirector"
        type="button"
        class="btn-outline"
        @click="browse.showCreateForm = !browse.showCreateForm"
      >
        {{ browse.showCreateForm ? 'Hide create form' : '+ Create tournament' }}
      </button>

      <div v-if="browse.showCreateForm && tournamentStore.hasDirector" class="create-form">
        <input v-model="browse.createName" type="text" class="field-input full" placeholder="Tournament name" />
        <div class="field-grid">
          <label class="field-label">
            Rounds
            <input v-model.number="browse.createRounds" type="number" min="1" max="20" class="field-input" />
          </label>
          <label class="field-label">
            Minutes
            <input v-model.number="browse.createClockMinutes" type="number" min="1" max="180" class="field-input" />
          </label>
          <label class="field-label">
            +sec
            <input v-model.number="browse.createClockIncrement" type="number" min="0" class="field-input" />
          </label>
        </div>
        <label class="field-label full">
          Format
          <select v-model="browse.createFormat" class="field-input">
            <option v-for="f in browse.FORMAT_OPTIONS" :key="f.id" :value="f.id">{{ f.label }}</option>
          </select>
        </label>
        <label v-if="tournamentStore.botsWithRegistry().length" class="check-row">
          <input v-model="browse.createAddAllBots" type="checkbox" />
          Add all {{ tournamentStore.botsWithRegistry().length }} registered bot(s) when creating
        </label>
        <button type="button" class="btn-sm full-width" :disabled="browse.creating" @click="browse.createTournament()">
          {{ browse.creating ? 'Creating…' : 'Create tournament' }}
        </button>
      </div>
      <p v-if="browse.directorMessage" class="hint-msg">{{ browse.directorMessage }}</p>
        </section>

        <section class="panel-section action-section nested-section">
          <div class="section-label">Your bots</div>
      <div class="action-row">
        <input v-model="browse.botNameInput" type="text" class="field-input" placeholder="Bot name prefix" />
        <button type="button" class="btn-sm" :disabled="browse.registering" @click="browse.registerBot()">
          + Add
        </button>
      </div>
      <div class="fleet-row">
        <label class="field-label fleet-count">
          Count
          <input v-model.number="browse.botFleetCount" type="number" min="2" max="8" class="field-input narrow" />
        </label>
        <button
          type="button"
          class="btn-sm"
          :disabled="browse.registeringFleet"
          @click="browse.registerBotFleet()"
        >
          {{ browse.registeringFleet ? 'Registering…' : 'Register fleet' }}
        </button>
      </div>

      <ul v-if="tournamentStore.bots.length" class="bot-roster">
        <li
          v-for="b in tournamentStore.bots"
          :key="b.id"
          :class="{ active: tournamentStore.activeBotId === b.id }"
        >
          <button type="button" class="bot-name" @click="tournamentStore.setActiveBot(b.id)">
            {{ b.name }}
          </button>
          <span v-if="!b.registryId" class="warn-tag" title="Re-register for director add">no registry</span>
          <div class="bot-actions">
            <button
              type="button"
              class="link-sm"
              :disabled="!browse.selectedId || browse.detail?.status !== 'created' || browse.joining"
              @click="browse.joinAsBot(b.id)"
            >
              Join
            </button>
            <button
              type="button"
              class="link-sm"
              :disabled="!browse.selectedId"
              @click="browse.copyBotRunCommand(b.id)"
            >
              Cmd
            </button>
            <button type="button" class="link-sm danger" @click="tournamentStore.removeBot(b.id)">×</button>
          </div>
        </li>
      </ul>
      <p v-else class="hint-msg">Register two or more bots to test your own tournament solo.</p>

      <div v-if="tournamentStore.hasBot" class="action-buttons">
        <button
          type="button"
          class="btn-sm btn-accent"
          :disabled="!browse.selectedId || browse.detail?.status !== 'created' || browse.bulkBotAction"
          @click="browse.setupTestParticipation()"
        >
          {{ browse.bulkBotAction ? 'Working…' : 'Quick test setup' }}
        </button>
        <button
          type="button"
          class="btn-sm"
          :disabled="!browse.selectedId || browse.detail?.status !== 'created' || browse.bulkBotAction"
          @click="browse.joinAllBots()"
        >
          Join all
        </button>
      </div>
      <button
        v-if="tournamentStore.bots.length > 1"
        type="button"
        class="link-sm roster-clear"
        @click="tournamentStore.clearAllBots()"
      >
        Clear all bots
      </button>
      <p v-if="browse.botMessage" class="hint-msg">{{ browse.botMessage }}</p>
        </section>
      </details>

      <p class="status-foot">{{ browse.statusText }}</p>
    </div>

    <!-- Board nav when a tournament game is on the board -->
    <div v-if="browse.boardGameLoaded || gameStore.tournamentLiveActive" class="board-nav">
      <div class="nav-label">
        {{ gameStore.tournamentLiveActive ? 'Live on board' : 'Replay on board' }}
      </div>
      <div class="nav-btns">
        <button type="button" class="nav-btn" :disabled="gameStore.currentIndex === 0" @click="gameStore.goToMove(0)">
          ⏮
        </button>
        <button type="button" class="nav-btn" :disabled="gameStore.currentIndex === 0" @click="gameStore.backward()">
          ◀
        </button>
        <span class="nav-pos">{{ gameStore.currentIndex }} / {{ gameStore.boardStates.length - 1 }}</span>
        <button type="button" class="nav-btn" :disabled="gameStore.isAtLatest" @click="gameStore.forward()">
          ▶
        </button>
        <button
          type="button"
          class="nav-btn"
          :disabled="gameStore.isAtLatest"
          @click="gameStore.goToMove(gameStore.boardStates.length - 1)"
        >
          ⏭
        </button>
      </div>
      <button
        v-if="gameStore.tournamentLiveActive"
        type="button"
        class="stop-live"
        @click="gameStore.stopTournamentLiveWatch()"
      >
        Stop live stream
      </button>
    </div>

    <div v-if="browse.loadingBoard" class="loading-overlay">Loading game…</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { Trophy } from 'lucide-vue-next'
import TournamentMovesTab from './TournamentMovesTab.vue'
import { useTournamentBrowseStore } from '../../stores/tournamentBrowse'
import { useTournamentStore } from '../../stores/tournament'
import { useGameStore } from '../../stores/game'

const browse = useTournamentBrowseStore()
const tournamentStore = useTournamentStore()
const gameStore = useGameStore()

const detailSectionEl = ref<HTMLElement | null>(null)

/** Open setup accordion when no tournament is selected yet. */
const setupOpen = computed(() => !browse.detail)

watch(
  () => browse.selectedId,
  async (id) => {
    if (!id) return
    await nextTick()
    detailSectionEl.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }
)
</script>

<style scoped>
.tournament-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  position: relative;
  font-size: 13px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-section-header-bg);
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 14px;
  color: var(--color-panel-text);
}

.panel-title-ico {
  color: #b8860b;
}

.icon-btn {
  border: none;
  background: var(--color-control-hover);
  border-radius: 6px;
  width: 28px;
  height: 28px;
  cursor: pointer;
  font-size: 14px;
}

.panel-error {
  margin: 8px 12px 0;
  padding: 8px 10px;
  background: #fee2e2;
  color: #991b1b;
  border-radius: 6px;
  font-size: 12px;
}

.btn-sm {
  padding: 5px 10px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent-strong);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-sm:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.panel-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.panel-section {
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.detail-section {
  background: var(--color-muted-bg);
}

.detail-tabs {
  display: flex;
  gap: 4px;
  margin: 10px 0 8px;
  padding: 3px;
  background: var(--color-muted-bg);
  border-radius: 8px;
}

.detail-tab {
  flex: 1;
  border: none;
  background: transparent;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  color: var(--color-text-secondary);
}

.detail-tab.active {
  background: var(--color-card-bg);
  color: var(--color-panel-text);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.detail-tab:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.scope-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 11px;
}

.scope-opt {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
}

.games-block {
  margin-bottom: 10px;
}

.finished-games-list {
  max-height: 220px;
  overflow-y: auto;
}

.games-hint {
  margin: 0 0 8px;
  font-size: 11px;
  color: var(--color-text-secondary);
}

.loading-tag {
  font-size: 10px;
  font-weight: 600;
  text-transform: none;
  color: var(--color-text-secondary);
}

.compact-list li {
  padding: 6px 8px;
}

.setup-details {
  border-bottom: 1px solid var(--color-border);
}

.setup-summary {
  padding: 10px 12px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
  cursor: pointer;
  list-style: none;
  user-select: none;
}

.setup-summary::-webkit-details-marker {
  display: none;
}

.setup-summary::before {
  content: '▸ ';
}

.setup-details[open] .setup-summary::before {
  content: '▾ ';
}

.nested-section {
  border-bottom: none;
}

.nested-section:last-child {
  padding-bottom: 10px;
}

.section-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.finished-label {
  margin-top: 10px;
}

.row-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.link-sm {
  border: none;
  background: none;
  font-size: 10px;
  color: var(--color-accent-text);
  cursor: pointer;
  text-transform: none;
  font-weight: 600;
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.item-list li {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: background 0.1s;
}

.item-list li:hover {
  background: var(--color-panel-hover);
}

.item-list li.selected {
  background: var(--color-accent-soft);
}

.item-list li.live.selected {
  box-shadow: inset 0 0 0 1px #2d6a4f;
}

.item-title {
  display: block;
  font-weight: 600;
  line-height: 1.3;
  color: var(--color-panel-text);
}

.item-meta {
  display: block;
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

.live-pill {
  display: inline-block;
  background: #2d6a4f;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  margin-right: 4px;
  text-transform: uppercase;
}

.finished-list {
  max-height: 120px;
  overflow-y: auto;
}

.empty {
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: default;
}

.empty:hover {
  background: none !important;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
  flex: 1;
}

.status-chip {
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
  padding: 3px 7px;
  border-radius: 999px;
  flex-shrink: 0;
  pointer-events: none;
  user-select: none;
}

.status-chip.started {
  background: #2d6a4f;
  color: #fff;
}

.status-chip.finished {
  background: var(--color-control-hover);
  color: var(--color-text-secondary);
}

.status-chip.created {
  background: #4a5568;
  color: #fff;
}

.live-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}

.action-section {
  background: var(--color-muted-bg);
}

.action-row,
.identity-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 8px;
}

.fleet-row {
  display: flex;
  gap: 6px;
  align-items: flex-end;
  margin-bottom: 8px;
}

.fleet-count {
  flex: 0 0 auto;
}

.field-input.narrow {
  width: 52px;
  flex: none;
}

.bot-roster {
  list-style: none;
  margin: 0 0 8px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.bot-roster li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
  font-size: 12px;
}

.bot-roster li:last-child {
  border-bottom: none;
}

.bot-roster li.active {
  background: var(--color-accent-soft);
}

.bot-name {
  flex: 1;
  min-width: 0;
  text-align: left;
  border: none;
  background: none;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  color: var(--color-panel-text);
  padding: 0;
}

.bot-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.warn-tag {
  font-size: 9px;
  color: #b45309;
  text-transform: uppercase;
  font-weight: 700;
}

.link-sm.danger {
  color: #b91c1c;
}

.roster-clear {
  display: block;
  margin-top: 4px;
}

.identity-row {
  justify-content: space-between;
}

.identity-tag {
  font-size: 12px;
  font-weight: 600;
}

.field-input {
  flex: 1;
  min-width: 0;
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid var(--color-border-input);
  font-size: 12px;
  background: var(--color-input-bg);
  color: var(--color-input-text);
}

.field-input.full {
  width: 100%;
  margin-bottom: 8px;
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 6px;
  margin-bottom: 8px;
}

.field-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-text-secondary);
}

.field-label.full {
  margin-bottom: 8px;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  margin-bottom: 8px;
  color: var(--color-text-secondary);
}

.create-form {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

.btn-outline {
  width: 100%;
  padding: 7px 10px;
  border: 1px dashed var(--color-border);
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  color: var(--color-accent-text);
}

.btn-outline.full-width {
  margin-top: 6px;
}

.full-width {
  width: 100%;
}

.action-buttons {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}

.action-buttons .btn-sm {
  flex: 1;
}

.btn-accent {
  background: #2d6a4f;
}

.btn-start {
  background: #1d4ed8;
}

.hint-msg {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--color-text-secondary);
  line-height: 1.35;
}

.hint-msg.warn {
  color: #b45309;
}

.start-box {
  background: var(--color-muted-bg);
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 10px;
}

.start-meta {
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.btn-sm {
  padding: 5px 10px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent-strong);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
}

.btn-sm:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.standings-list {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
  max-height: 140px;
  overflow-y: auto;
}

.standings-list li {
  display: grid;
  grid-template-columns: 2rem 1fr auto;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}

.standings-list li:hover {
  background: var(--color-panel-hover);
}

.standings-list li.selected {
  background: var(--color-accent-soft);
  font-weight: 600;
}

.rank {
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

.pts {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.round-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.round-btn {
  min-width: 28px;
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-input-bg);
  font-size: 12px;
  cursor: pointer;
}

.round-btn.active {
  background: var(--color-accent-strong);
  color: #fff;
  border-color: transparent;
}

.games-list {
  margin-top: 4px;
}

.status-foot {
  padding: 8px 12px;
  font-size: 11px;
  color: var(--color-text-secondary);
  border-top: 1px solid var(--color-border);
  flex-shrink: 0;
}

.board-nav {
  padding: 10px 12px;
  border-top: 1px solid var(--color-border);
  background: var(--color-muted-bg);
  flex-shrink: 0;
}

.nav-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.nav-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-btn {
  border: 1px solid var(--color-border);
  background: var(--color-input-bg);
  border-radius: 6px;
  width: 32px;
  height: 28px;
  cursor: pointer;
  font-size: 11px;
}

.nav-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.nav-pos {
  flex: 1;
  text-align: center;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

.stop-live {
  margin-top: 8px;
  width: 100%;
  padding: 6px;
  border: none;
  border-radius: 6px;
  background: var(--color-control-hover);
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  color: var(--color-text-secondary);
}

.loading-overlay {
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  z-index: 2;
}
</style>
