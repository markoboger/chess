<template>
  <header class="navbar">
    <div class="navbar-inner">
      <!-- Brand -->
      <a class="brand" @click.prevent="emit('new-game')">
        <span class="brand-icon">♛</span>
        <span class="brand-name">Chess</span>
      </a>

      <!-- Desktop nav items -->
      <nav class="desktop-nav">
        <!-- Game menu -->
        <div class="nav-item" :class="{ open: openMenu === 'game' }">
          <button class="nav-btn" @click.stop="toggle('game')">
            <Swords :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
            <span>Game</span>
            <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'game'">
            <div class="dropdown-group">Mode</div>
            <button class="dd-item" :class="{ active: gameStore.gameMode === 'hvh' }" @click="setMode('hvh')">
              <span class="dd-check">{{ gameStore.gameMode === 'hvh' ? '●' : '' }}</span> Human vs Human
            </button>
            <button class="dd-item" :class="{ active: gameStore.gameMode === 'hvc' }" @click="setMode('hvc')">
              <span class="dd-check">{{ gameStore.gameMode === 'hvc' ? '●' : '' }}</span> Human vs Computer
            </button>
            <button class="dd-item" :class="{ active: gameStore.gameMode === 'cvc' }" @click="setMode('cvc')">
              <span class="dd-check">{{ gameStore.gameMode === 'cvc' ? '●' : '' }}</span> Computer vs Computer
            </button>
          </div>
        </div>

        <!-- Clock menu -->
        <div class="nav-item" :class="{ open: openMenu === 'clock' }">
          <button class="nav-btn" @click.stop="toggle('clock')">
            <ClockIcon :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
            <span>Clock</span>
            <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'clock'">
            <div class="dropdown-group">Time Control</div>
            <button
              v-for="p in CLOCK_PRESETS"
              :key="p.label"
              class="dd-item"
              :class="{ active: isActivePreset(p.mode) }"
              @click="setClock(p.mode)"
            >
              <span class="dd-check">{{ isActivePreset(p.mode) ? '●' : '' }}</span> {{ p.label }}
            </button>
          </div>
        </div>

        <!-- Strategy menu -->
        <div class="nav-item" :class="{ open: openMenu === 'strategy' }">
          <button class="nav-btn nav-btn-strategy" @click.stop="toggle('strategy')">
            <Brain :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
            <span class="nav-btn-strategy-text">{{ strategyButtonLabel }}</span>
            <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'strategy'">
            <div class="dropdown-group">White</div>
            <button
              v-for="strategy in COMPUTER_STRATEGIES"
              :key="'w-' + strategy.id"
              class="dd-item"
              :class="{ active: gameStore.whiteComputerStrategy === strategy.id }"
              @click="setStrategy('white', strategy.id)"
            >
              <span class="dd-check">{{ gameStore.whiteComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
            </button>
            <div class="dd-sep"></div>
            <div class="dropdown-group">Black</div>
            <button
              v-for="strategy in COMPUTER_STRATEGIES"
              :key="'b-' + strategy.id"
              class="dd-item"
              :class="{ active: gameStore.blackComputerStrategy === strategy.id }"
              @click="setStrategy('black', strategy.id)"
            >
              <span class="dd-check">{{ gameStore.blackComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
            </button>
          </div>
        </div>

        <!-- View menu -->
        <div class="nav-item" :class="{ open: openMenu === 'view' }">
          <button class="nav-btn" @click.stop="toggle('view')">
            <Eye :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
            <span>View</span>
            <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'view'">
            <button class="dd-item" @click="toggleLegal">
              <span class="dd-check">{{ gameStore.showLegalMoves ? '✓' : '' }}</span> Show Legal Moves
            </button>
          </div>
        </div>

        <!-- File menu (import / export only — matches desktop File → Import/Export) -->
        <div class="nav-item" :class="{ open: openMenu === 'file' }">
          <button class="nav-btn" @click.stop="toggle('file')">
            <FileText :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
            <span>File</span>
            <span class="chevron">▾</span>
          </button>
          <div class="dropdown" v-if="openMenu === 'file'">
            <div class="dropdown-group">Import</div>
            <button type="button" class="dd-item" @click="openImport('fen')">
              <ClipboardPaste :size="16" :stroke-width="2" class="dd-ico" aria-hidden="true" />
              Paste FEN
            </button>
            <button type="button" class="dd-item" @click="openImport('pgn')">
              <ClipboardPaste :size="16" :stroke-width="2" class="dd-ico" aria-hidden="true" />
              Paste PGN
            </button>
            <div class="dd-sep"></div>
            <div class="dropdown-group">Export</div>
            <button type="button" class="dd-item" @click="copyFen">
              <Copy :size="16" :stroke-width="2" class="dd-ico" aria-hidden="true" />
              Copy FEN
            </button>
            <button type="button" class="dd-item" @click="copyPgn">
              <Copy :size="16" :stroke-width="2" class="dd-ico" aria-hidden="true" />
              Copy PGN
            </button>
          </div>
        </div>

        <!-- Openings menu (desktop app: Openings → by family / ECO) -->
        <div class="nav-item nav-item-openings" :class="{ open: openMenu === 'openings' }">
          <button class="nav-btn" @click.stop="toggle('openings')">
            <BookOpen :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
            <span>Openings</span>
            <span class="chevron">▾</span>
          </button>
          <div v-if="openMenu === 'openings'" class="dropdown dropdown-openings">
            <div v-if="openingMenuLoading" class="dropdown-hint">Loading book…</div>
            <div v-else-if="openingsByFamily.length === 0" class="dropdown-hint">No openings loaded.</div>
            <div v-else class="opening-scroll">
              <p class="opening-menu-hint">Open a letter group (ECO A–E) to browse openings in that category.</p>
              <details
                v-for="[fam, items] in openingsByFamily"
                :key="fam"
                class="opening-family-block"
              >
                <summary class="opening-family-summary">
                  <span class="opening-family-title">{{ openingFamilyDesc(fam) }}</span>
                  <span class="opening-family-count">{{ items.length }}</span>
                </summary>
                <div class="opening-family-body">
                  <button
                    v-for="o in sortOpeningsInFamily(items)"
                    :key="o.eco + '|' + o.moves"
                    type="button"
                    class="dd-item dd-item-opening dd-item-opening-nested"
                    @click="applyOpening(o)"
                  >
                    <span class="opening-eco">{{ o.eco }}</span>
                    <span class="opening-name">{{ o.name }}</span>
                    <span class="opening-plies">{{ movePlies(o.moves) }} plies</span>
                  </button>
                </div>
              </details>
            </div>
          </div>
        </div>

        <!-- Puzzles -->
        <button
          type="button"
          class="nav-btn nav-btn-puzzles"
          :class="{ 'nav-btn-active': activeView === 'puzzles' }"
          @click="emit('toggle-puzzles')"
        >
          <Puzzle :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
          <span>Puzzles</span>
        </button>

        <!-- Experiments (desktop: Experiments → Browse Games…) -->
        <div class="nav-item" :class="{ open: openMenu === 'experiments' }">
          <button type="button" class="nav-btn" @click.stop="toggle('experiments')">
            <FlaskConical :size="16" :stroke-width="2" class="nav-ico" aria-hidden="true" />
            <span>Experiments</span>
            <span class="chevron">▾</span>
          </button>
          <div v-if="openMenu === 'experiments'" class="dropdown">
            <button type="button" class="dd-item" @click="openBrowseExperiments">
              <ListTree :size="16" :stroke-width="2" class="dd-ico" aria-hidden="true" />
              Browse experiment games…
            </button>
          </div>
        </div>
      </nav>

      <!-- Right side: theme toggle -->
      <div class="navbar-right">
        <button
          type="button"
          class="icon-btn"
          :title="uiStore.theme === 'dark' ? 'Light mode' : 'Dark mode'"
          @click="uiStore.toggleTheme()"
        >
          <Sun v-if="uiStore.theme === 'dark'" :size="20" :stroke-width="2" />
          <Moon v-else :size="20" :stroke-width="2" />
        </button>
        <!-- Burger button (mobile) -->
        <button class="burger-btn" :class="{ open: mobileOpen }" @click="mobileOpen = !mobileOpen" aria-label="Menu">
          <span></span><span></span><span></span>
        </button>
      </div>
    </div>

    <!-- Mobile drawer -->
    <Transition name="drawer">
      <div v-if="mobileOpen" class="mobile-drawer" @click.self="mobileOpen = false">
        <div class="drawer-section">
          <div class="drawer-label">Game Mode</div>
          <button class="drawer-item" :class="{ active: gameStore.gameMode === 'hvh' }" @click="setMode('hvh'); mobileOpen=false">
            <span class="dd-check">{{ gameStore.gameMode === 'hvh' ? '●' : '' }}</span> Human vs Human
          </button>
          <button class="drawer-item" :class="{ active: gameStore.gameMode === 'hvc' }" @click="setMode('hvc'); mobileOpen=false">
            <span class="dd-check">{{ gameStore.gameMode === 'hvc' ? '●' : '' }}</span> Human vs Computer
          </button>
          <button class="drawer-item" :class="{ active: gameStore.gameMode === 'cvc' }" @click="setMode('cvc'); mobileOpen=false">
            <span class="dd-check">{{ gameStore.gameMode === 'cvc' ? '●' : '' }}</span> Computer vs Computer
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">Clock</div>
          <button
            v-for="p in CLOCK_PRESETS"
            :key="p.label"
            class="drawer-item"
            :class="{ active: isActivePreset(p.mode) }"
            @click="setClock(p.mode); mobileOpen=false"
          >
            <span class="dd-check">{{ isActivePreset(p.mode) ? '●' : '' }}</span> {{ p.label }}
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">White Strategy</div>
          <button
            v-for="strategy in COMPUTER_STRATEGIES"
            :key="'mw-' + strategy.id"
            class="drawer-item"
            :class="{ active: gameStore.whiteComputerStrategy === strategy.id }"
            @click="setStrategy('white', strategy.id); mobileOpen=false"
          >
            <span class="dd-check">{{ gameStore.whiteComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">Black Strategy</div>
          <button
            v-for="strategy in COMPUTER_STRATEGIES"
            :key="'mb-' + strategy.id"
            class="drawer-item"
            :class="{ active: gameStore.blackComputerStrategy === strategy.id }"
            @click="setStrategy('black', strategy.id); mobileOpen=false"
          >
            <span class="dd-check">{{ gameStore.blackComputerStrategy === strategy.id ? '●' : '' }}</span> {{ strategy.label }}
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">View</div>
          <button class="drawer-item" @click="toggleLegal; mobileOpen=false">
            <span class="dd-check">{{ gameStore.showLegalMoves ? '✓' : '' }}</span> Show Legal Moves
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">File</div>
          <button type="button" class="drawer-item" @click="openImport('fen'); mobileOpen=false">
            <ClipboardPaste :size="16" :stroke-width="2" class="dd-ico" /> Paste FEN
          </button>
          <button type="button" class="drawer-item" @click="openImport('pgn'); mobileOpen=false">
            <ClipboardPaste :size="16" :stroke-width="2" class="dd-ico" /> Paste PGN
          </button>
          <button type="button" class="drawer-item" @click="copyFen; mobileOpen=false">
            <Copy :size="16" :stroke-width="2" class="dd-ico" /> Copy FEN
          </button>
          <button type="button" class="drawer-item" @click="copyPgn; mobileOpen=false">
            <Copy :size="16" :stroke-width="2" class="dd-ico" /> Copy PGN
          </button>
        </div>

        <div class="drawer-section drawer-openings">
          <div class="drawer-label">Openings</div>
          <div v-if="mobileCatalogLoading" class="drawer-hint">Loading book…</div>
          <div v-else-if="openingsByFamily.length === 0" class="drawer-hint">No openings loaded.</div>
          <div v-else class="drawer-opening-scroll">
            <p class="drawer-opening-hint">Tap a letter group (ECO A–E) to expand.</p>
            <details
              v-for="[fam, items] in openingsByFamily"
              :key="'m-'+fam"
              class="opening-family-block opening-family-block--drawer"
            >
              <summary class="opening-family-summary opening-family-summary--drawer">
                <span class="opening-family-title">{{ openingFamilyDesc(fam) }}</span>
                <span class="opening-family-count">{{ items.length }}</span>
              </summary>
              <div class="opening-family-body opening-family-body--drawer">
                <button
                  v-for="o in sortOpeningsInFamily(items)"
                  :key="'mo-'+o.eco+o.moves"
                  type="button"
                  class="drawer-item drawer-item-compact drawer-item-opening-row"
                  @click="applyOpening(o); mobileOpen=false"
                >
                  <span class="opening-eco">{{ o.eco }}</span>
                  <span class="opening-line-name">{{ o.name }}</span>
                </button>
              </div>
            </details>
          </div>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">Experiments</div>
          <button type="button" class="drawer-item" @click="openBrowseExperiments; mobileOpen=false">
            <ListTree :size="16" :stroke-width="2" class="dd-ico" /> Browse experiment games…
          </button>
        </div>

        <div class="drawer-section">
          <button type="button" class="drawer-item puzzles-item" @click="emit('toggle-puzzles'); mobileOpen=false">
            <Puzzle :size="16" :stroke-width="2" class="dd-ico" /> Puzzles
          </button>
        </div>

        <div class="drawer-section">
          <div class="drawer-label">Appearance</div>
          <button type="button" class="drawer-item" @click="uiStore.toggleTheme(); mobileOpen = false">
            <Sun v-if="uiStore.theme === 'dark'" :size="18" :stroke-width="2" class="dd-ico" aria-hidden="true" />
            <Moon v-else :size="18" :stroke-width="2" class="dd-ico" aria-hidden="true" />
            {{ uiStore.theme === 'dark' ? 'Light mode' : 'Dark mode' }}
          </button>
        </div>
      </div>
    </Transition>
  </header>

  <!-- Import dialog (shared between desktop/mobile) -->
  <Teleport to="body">
    <div v-if="showImport" class="dialog-overlay" @click.self="showImport = false">
      <div class="dialog">
        <div class="dialog-title">{{ importType === 'fen' ? 'Paste FEN' : 'Paste PGN' }}</div>
        <textarea
          v-model="importText"
          class="dialog-textarea"
          :placeholder="importType === 'fen' ? 'rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1' : '1. e4 e5 2. Nf3 Nc6 ...'"
          rows="5"
          autofocus
        ></textarea>
        <div class="dialog-row">
          <button class="dialog-btn cancel" @click="showImport = false">Cancel</button>
          <button class="dialog-btn ok" @click="doImport">Import</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import {
  useGameStore,
  CLOCK_PRESETS,
  COMPUTER_STRATEGIES,
  type ClockMode,
  type GameMode,
  type ComputerSide,
  type ComputerStrategyId,
} from '../../stores/game'
import { useUIStore } from '../../stores/ui'
import { useOpeningStore, type OpeningInfo } from '../../stores/opening'
import {
  Swords,
  Clock as ClockIcon,
  Brain,
  Eye,
  FileText,
  BookOpen,
  Puzzle,
  FlaskConical,
  ClipboardPaste,
  Copy,
  Sun,
  Moon,
  ListTree,
} from 'lucide-vue-next'

const props = defineProps<{ activeView?: 'game' | 'puzzles' }>()
const emit = defineEmits<{
  'new-game': []
  'toggle-puzzles': []
  'browse-experiments': []
  'show-game': []
}>()

const gameStore = useGameStore()
const uiStore = useUIStore()
const openingStore = useOpeningStore()

const openMenu = ref<string | null>(null)
const mobileOpen = ref(false)
const openingMenuLoading = ref(false)
const mobileCatalogLoading = ref(false)

function strategyLabel(id: ComputerStrategyId): string {
  return COMPUTER_STRATEGIES.find(s => s.id === id)?.label ?? id
}

const strategyButtonLabel = computed(() => {
  const mode = gameStore.gameMode
  if (mode === 'hvh') return 'Strategy'
  if (mode === 'hvc') return `Strategy: ${strategyLabel(gameStore.blackComputerStrategy)}`
  // cvc: show both if different, else just the one
  const w = strategyLabel(gameStore.whiteComputerStrategy)
  const b = strategyLabel(gameStore.blackComputerStrategy)
  return w === b ? `Strategy: ${w}` : `Strategy: ${w} / ${b}`
})

async function toggle(menu: string) {
  openMenu.value = openMenu.value === menu ? null : menu
  if (openMenu.value === 'openings') {
    openingMenuLoading.value = true
    try {
      await openingStore.loadCatalog()
    } finally {
      openingMenuLoading.value = false
    }
  }
}

/** ECO volume letter → human-readable category (matches common ECO grouping). */
function openingFamilyDesc(c: string): string {
  switch (c) {
    case 'A':
      return 'A — Flank & irregular'
    case 'B':
      return 'B — Semi-open (1.e4, Black asymmetrical)'
    case 'C':
      return 'C — Open games (1.e4 e5 & similar)'
    case 'D':
      return 'D — Closed & semi-closed'
    case 'E':
      return 'E — Indian defenses & others'
    default:
      return `${c || '?'} — Other`
  }
}

function sortOpeningsInFamily(items: OpeningInfo[]): OpeningInfo[] {
  return [...items].sort((a, b) => {
    const byEco = a.eco.localeCompare(b.eco, undefined, { numeric: true, sensitivity: 'base' })
    if (byEco !== 0) return byEco
    return a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
  })
}

/** Menu order: open & double-king-pawn first, then closed, then semi-open, Indian, flank; unknown letters last. */
const ECO_FAMILY_MENU_ORDER = ['C', 'D', 'B', 'E', 'A']

const openingsByFamily = computed(() => {
  const fam = new Map<string, OpeningInfo[]>()
  for (const o of openingStore.catalog) {
    const key = (o.eco.charAt(0) || '?').toUpperCase()
    fam.set(key, [...(fam.get(key) ?? []), o])
  }
  const entries = [...fam.entries()]
  entries.sort((a, b) => {
    const ia = ECO_FAMILY_MENU_ORDER.indexOf(a[0])
    const ib = ECO_FAMILY_MENU_ORDER.indexOf(b[0])
    const ra = ia === -1 ? 100 + a[0].charCodeAt(0) : ia
    const rb = ib === -1 ? 100 + b[0].charCodeAt(0) : ib
    if (ra !== rb) return ra - rb
    return a[0].localeCompare(b[0])
  })
  return entries
})

function movePlies(movesPgn: string): number {
  const t = movesPgn
    .trim()
    .split(/\s+/)
    .filter((x) => x && !/^\d+\.?$/.test(x))
  return t.length
}

async function applyOpening(o: OpeningInfo) {
  closeAll()
  mobileOpen.value = false
  gameStore.puzzleMode = false
  gameStore.setGameMode('hvh')
  emit('show-game')
  await gameStore.loadPgnString(o.moves)
}

function closeAll() {
  openMenu.value = null
}

function handleOutsideClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('.nav-item') && !target.closest('.dropdown')) {
    closeAll()
  }
}

onMounted(() => document.addEventListener('click', handleOutsideClick))
onUnmounted(() => document.removeEventListener('click', handleOutsideClick))

watch(
  () => mobileOpen.value,
  async (open) => {
    if (!open) return
    mobileCatalogLoading.value = true
    try {
      await openingStore.loadCatalog()
    } finally {
      mobileCatalogLoading.value = false
    }
  }
)

function setMode(mode: GameMode) {
  gameStore.setGameMode(mode)
  closeAll()
}

function isActivePreset(mode: ClockMode): boolean {
  if (mode.kind === 'none' && gameStore.clockMode.kind === 'none') return true
  if (mode.kind === 'timed' && gameStore.clockMode.kind === 'timed') {
    return mode.initialMs === gameStore.clockMode.initialMs && mode.incrementMs === gameStore.clockMode.incrementMs
  }
  return false
}

function setClock(mode: ClockMode) {
  gameStore.setClockMode(mode)
  closeAll()
}

function setStrategy(side: ComputerSide, strategy: ComputerStrategyId) {
  gameStore.setComputerStrategy(side, strategy)
  closeAll()
}

function toggleLegal() {
  gameStore.showLegalMoves = !gameStore.showLegalMoves
  closeAll()
}

// Import / Export
const showImport = ref(false)
const importType = ref<'fen' | 'pgn'>('fen')
const importText = ref('')

function openImport(type: 'fen' | 'pgn') {
  importType.value = type
  importText.value = ''
  showImport.value = true
  closeAll()
}

function openBrowseExperiments() {
  closeAll()
  emit('browse-experiments')
}

async function doImport() {
  if (!importText.value.trim()) return
  const ok =
    importType.value === 'fen'
      ? await gameStore.loadFenString(importText.value)
      : await gameStore.loadPgnString(importText.value)
  showImport.value = false
  if (ok) {
    gameStore.puzzleMode = false
    emit('show-game')
  }
}

function copyFen() {
  navigator.clipboard.writeText(gameStore.fen)
  closeAll()
}

function copyPgn() {
  navigator.clipboard.writeText(gameStore.pgnText)
  closeAll()
}
</script>

<style scoped>
/* ── Navbar shell ─────────────────────────────────────────────────── */
.navbar {
  background: #312e2b;
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0,0,0,0.35);
}

.navbar-inner {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 16px;
  height: 52px;
  max-width: 1400px;
  margin: 0 auto;
}

/* ── Brand ─────────────────────────────────────────────────────────── */
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  cursor: pointer;
  padding: 0 12px 0 0;
  margin-right: 8px;
  border-right: 1px solid rgba(255,255,255,0.12);
  flex-shrink: 0;
}

.brand-icon {
  font-size: 22px;
  line-height: 1;
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.3px;
}

/* ── Desktop nav ───────────────────────────────────────────────────── */
.desktop-nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
}

.nav-item {
  position: relative;
}

.nav-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.85);
  font-size: 14px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: background 0.12s, color 0.12s;
  white-space: nowrap;
}

.nav-btn:hover,
.nav-item.open .nav-btn {
  background: rgba(0,0,0,0.16);
  color: #fff;
}

.nav-btn-active {
  background: rgba(50, 85, 17, 0.5) !important;
  color: #f1ffd8 !important;
}

.nav-btn-puzzles {
  margin-left: 4px;
}

.chevron {
  font-size: 10px;
  opacity: 0.7;
}

/* ── Dropdown ──────────────────────────────────────────────────────── */
.dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: 210px;
  background: var(--color-panel-bg);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.18);
  padding: 6px 0;
  z-index: 200;
  animation: fadeInDown 0.1s ease;
  border: 1px solid var(--color-border);
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

@keyframes fadeInDown {
  from { opacity: 0; transform: translateY(-6px); }
  to   { opacity: 1; transform: translateY(0); }
}

.dropdown-group {
  padding: 4px 14px 2px;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-panel-muted);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.dd-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 14px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13.5px;
  text-align: left;
  color: var(--color-panel-text);
  transition: background 0.08s, color 0.15s ease;
}

.dd-item:hover { background: var(--color-panel-hover); }
.dd-item.active { font-weight: 700; color: var(--color-accent-text); }

.dd-check {
  width: 16px;
  text-align: center;
  font-size: 10px;
  color: var(--color-accent);
  flex-shrink: 0;
}

.dd-sep {
  height: 1px;
  background: var(--color-panel-sep);
  margin: 4px 0;
}

.nav-ico {
  flex-shrink: 0;
  opacity: 0.92;
  color: rgba(255, 255, 255, 0.78);
}

.dd-ico {
  flex-shrink: 0;
  color: var(--color-text-secondary);
}

.nav-btn-strategy {
  max-width: min(280px, 28vw);
}

.nav-btn-strategy-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dropdown-openings {
  min-width: 320px;
  max-width: min(420px, 92vw);
  padding: 0;
}

.dropdown-hint {
  padding: 12px 16px;
  font-size: 13px;
  color: var(--color-panel-muted);
}

.opening-scroll {
  max-height: min(420px, 60vh);
  overflow-y: auto;
  padding: 4px 0 8px;
}

.opening-menu-hint {
  margin: 0;
  padding: 8px 14px 10px;
  font-size: 12px;
  line-height: 1.35;
  color: var(--color-panel-muted);
  border-bottom: 1px solid var(--color-panel-sep);
}

/* ECO family (A–E): one collapsible block per category */
.opening-family-block {
  border-bottom: 1px solid var(--color-panel-sep);
}
.opening-family-block:last-child {
  border-bottom: none;
}

.opening-family-summary {
  list-style: none;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 14px 8px;
  user-select: none;
  transition: background 0.1s;
}
.opening-family-summary::-webkit-details-marker {
  display: none;
}
.opening-family-summary::marker {
  display: none;
}
.opening-family-summary:hover {
  background: var(--color-panel-hover);
}

.opening-family-title {
  font-weight: 700;
  font-size: 12px;
  line-height: 1.35;
  color: var(--color-panel-text);
  flex: 1;
  min-width: 0;
}

.opening-family-count {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--color-panel-muted);
  background: var(--color-control-hover);
  padding: 2px 8px;
  border-radius: 999px;
}

.opening-family-body {
  padding: 0 0 6px;
}

.dd-item-opening.dd-item-opening-nested {
  padding-left: 18px;
  padding-right: 14px;
}

.dropdown-sub {
  padding: 6px 14px 2px;
  font-size: 11px;
  font-weight: 700;
  color: var(--color-panel-muted);
  letter-spacing: 0.02em;
}

.dd-item-opening {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px 10px;
  align-items: baseline;
}

.opening-eco {
  font-family: ui-monospace, 'Cascadia Code', monospace;
  font-weight: 700;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.opening-name {
  font-size: 13px;
  color: var(--color-panel-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.opening-plies {
  font-size: 11px;
  color: var(--color-panel-muted);
  white-space: nowrap;
}

/* ── Right side ────────────────────────────────────────────────────── */
.navbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.icon-btn {
  background: none;
  border: none;
  color: rgba(255,255,255,0.8);
  font-size: 18px;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 6px;
  line-height: 1;
  transition: background 0.12s;
}

.icon-btn:hover { background: rgba(255,255,255,0.12); }

/* ── Burger button ─────────────────────────────────────────────────── */
.burger-btn {
  display: none;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 5px;
  width: 36px;
  height: 36px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.12s;
}

.burger-btn span {
  display: block;
  width: 20px;
  height: 2px;
  background: rgba(255,255,255,0.85);
  border-radius: 2px;
  transition: all 0.25s ease;
  transform-origin: center;
}

.burger-btn:hover { background: rgba(255,255,255,0.12); }

.burger-btn.open span:nth-child(1) { transform: translateY(7px) rotate(45deg); }
.burger-btn.open span:nth-child(2) { opacity: 0; transform: scaleX(0); }
.burger-btn.open span:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }

/* ── Mobile drawer ─────────────────────────────────────────────────── */
.mobile-drawer {
  background: #222;
  border-top: 1px solid rgba(255,255,255,0.08);
  padding: 8px 0 16px;
  overflow-y: auto;
  max-height: calc(100vh - 52px);
}

.drawer-section {
  border-bottom: 1px solid rgba(255,255,255,0.06);
  padding: 8px 0;
}

.drawer-section:last-child { border-bottom: none; }

.drawer-label {
  padding: 4px 20px 2px;
  font-size: 11px;
  font-weight: 700;
  color: rgba(255,255,255,0.4);
  text-transform: uppercase;
  letter-spacing: 0.6px;
}

.drawer-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 20px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 14px;
  text-align: left;
  color: rgba(255,255,255,0.8);
  transition: background 0.08s;
}

.drawer-item:hover { background: rgba(255,255,255,0.07); }
.drawer-item.active { color: #a8d96b; font-weight: 600; }

.puzzles-item {
  color: #a8d96b;
  font-weight: 600;
}

.drawer-hint {
  padding: 6px 20px 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
}

.drawer-openings .drawer-opening-scroll {
  max-height: min(360px, 45vh);
  overflow-y: auto;
}

.drawer-opening-hint {
  margin: 0;
  padding: 6px 16px 10px;
  font-size: 12px;
  line-height: 1.35;
  color: rgba(255, 255, 255, 0.45);
}

.opening-family-block--drawer {
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.opening-family-block--drawer:last-child {
  border-bottom: none;
}

.opening-family-summary--drawer {
  padding: 10px 16px 8px;
}
.opening-family-summary--drawer:hover {
  background: rgba(255, 255, 255, 0.06);
}
.opening-family-summary--drawer .opening-family-title {
  color: rgba(255, 255, 255, 0.92);
  font-size: 12px;
}
.opening-family-summary--drawer .opening-family-count {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.75);
}

.opening-family-body--drawer {
  padding-bottom: 4px;
}

.drawer-item-opening-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px 10px;
  align-items: baseline;
  text-align: left;
}

.opening-line-name {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.88);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.drawer-item-opening-row .opening-eco {
  color: rgba(255, 255, 255, 0.55);
}

.drawer-sub {
  padding: 8px 20px 4px;
  font-size: 11px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 0.04em;
}

.drawer-item-compact {
  padding: 6px 20px;
  font-size: 13px;
}

.drawer-item .dd-ico {
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.55);
}

/* ── Drawer transition ─────────────────────────────────────────────── */
.drawer-enter-active, .drawer-leave-active { transition: all 0.22s ease; }
.drawer-enter-from, .drawer-leave-to { opacity: 0; transform: translateY(-8px); }

/* ── Import dialog ─────────────────────────────────────────────────── */
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-dialog-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: var(--color-dialog-bg);
  border-radius: 12px;
  padding: 24px;
  width: 460px;
  max-width: 92vw;
  box-shadow: 0 16px 48px rgba(0,0,0,0.25);
  border: 1px solid var(--color-border);
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.dialog-title {
  font-weight: 700;
  font-size: 16px;
  margin-bottom: 14px;
  color: var(--color-panel-text);
}

.dialog-textarea {
  width: 100%;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 12px;
  padding: 10px;
  border: 1.5px solid var(--color-border-input);
  border-radius: 6px;
  resize: vertical;
  outline: none;
  color: var(--color-input-text);
  background: var(--color-input-bg);
  line-height: 1.5;
}

.dialog-textarea:focus { border-color: var(--color-accent); }

.dialog-row {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.dialog-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.dialog-btn.cancel { background: var(--color-control-hover); color: var(--color-text-secondary); }
.dialog-btn.cancel:hover { background: var(--color-control-hover2); }
.dialog-btn.ok { background: var(--color-accent-strong); color: #fff; }
.dialog-btn.ok:hover { background: var(--color-accent-hover); }

/* ── Responsive: show burger, hide desktop nav ─────────────────────── */
@media (max-width: 768px) {
  .desktop-nav { display: none; }
  .burger-btn   { display: flex; }
}
</style>
