# Lecture 8: Vue 3 Responsive Web UI

## Overview

This lecture demonstrates how to build a **modern, responsive web interface** for the chess game using **Vue 3**, **TypeScript**, and **Tailwind CSS**. The UI adapts seamlessly to different screen sizes (mobile, tablet, desktop) and integrates with the microservices backend.

## Learning Objectives

1. **Vue 3 Composition API**: Modern reactive component architecture
2. **TypeScript Integration**: Type-safe Vue components and stores
3. **Responsive Design**: Mobile-first approach with Tailwind CSS
4. **State Management**: Pinia for application state
5. **API Integration**: RESTful communication with microservices backend
6. **Modern Build Tools**: Vite for fast development and optimized production builds

## Architecture

### Technology Stack

- **Framework**: Vue 3 (Composition API)
- **Language**: TypeScript
- **Build Tool**: Vite
- **Styling**: Tailwind CSS v4
- **State Management**: Pinia
- **HTTP Client**: Axios
- **Chess Logic**: Chess.js (client-side validation)
- **Testing**: Vitest (unit), Playwright (E2E)
- **Deployment**: Docker + Nginx

### Project Structure

```
frontend/
├── src/
│   ├── api/
│   │   ├── client.ts              # Axios instance configuration
│   │   └── game-api.ts            # Game Service API client
│   ├── components/
│   │   ├── board/
│   │   │   └── ChessBoard.vue     # Interactive chess board
│   │   ├── controls/
│   │   │   ├── GameControls.vue   # New game, reset buttons
│   │   │   └── MoveHistory.vue    # Move list and PGN display
│   │   └── ui/
│   │       └── ThemeToggle.vue    # Dark/light mode toggle
│   ├── stores/
│   │   ├── game.ts                # Game state management (Pinia)
│   │   └── ui.ts                  # UI state (theme, mobile menu)
│   ├── types/
│   │   ├── game.ts                # Game-related TypeScript types
│   │   └── api.ts                 # API request/response types
│   ├── style.css                  # Tailwind imports + custom styles
│   ├── App.vue                    # Root component
│   └── main.ts                    # Application entry point
├── public/                        # Static assets
├── index.html                     # HTML template
├── package.json                   # Dependencies and scripts
├── vite.config.ts                 # Vite configuration
├── tailwind.config.js             # Tailwind configuration
├── postcss.config.js              # PostCSS configuration
├── tsconfig.json                  # TypeScript configuration
└── nginx.conf                     # Nginx configuration for Docker
```

## Key Features

### 1. Responsive Chess Board

**Mobile (<768px):**
- Full-width board (max 400px)
- Smaller piece icons (text-4xl)
- Touch-friendly tap-to-select interface

**Tablet (768-1024px):**
- Board max-width: 500px
- Split view with sidebar

**Desktop (>1024px):**
- Board max-width: 600px
- Three-column layout with persistent controls

### 2. Interactive Gameplay

- **Click-to-Select**: Click a piece to highlight legal moves
- **Move Validation**: Client-side validation with Chess.js + server confirmation
- **Visual Feedback**:
  - Selected square: Blue ring
  - Legal moves: Green dots
  - Last move: Yellow highlight
  - Check: Orange warning
  - Checkmate/Stalemate: Game-over banner

### 3. State Management with Pinia

**Game Store** (`stores/game.ts`):
```typescript
- State: gameId, fen, pgn, status, moveHistory, selectedSquare, legalMoves
- Computed: turn, turnColor, isCheck, isCheckmate, isStalemate, isDraw
- Actions: createGame(), makeMove(), selectSquare(), loadFen(), resetGame()
```

**UI Store** (`stores/ui.ts`):
```typescript
- State: theme (light/dark), mobileMenuOpen
- Actions: toggleTheme(), toggleMobileMenu()
- Persistence: localStorage for theme preference
```

### 4. Dark/Light Theme

- System preference detection
- Manual toggle button
- LocalStorage persistence
- Smooth transitions
- Tailwind dark mode class strategy

### 5. API Integration

**Endpoints** (proxied through API Gateway at `http://localhost:8080`):
- `POST /games` - Create new game
- `GET /games/:id` - Get game state
- `POST /games/:id/moves` - Make a move
- `POST /games/:id/fen` - Load position from FEN
- `DELETE /games/:id` - Delete game

**Error Handling**:
- Axios interceptors for global error logging
- Per-request error states in store
- User-friendly error messages in UI

## Development Workflow

### Local Development

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies
npm install

# Start development server (with hot reload)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

**Development Server**: `http://localhost:5173`

**Environment Variables** (create `.env` file):
```
VITE_API_URL=http://localhost:8080
```

### Running with Docker Compose

```bash
# From project root
docker-compose up --build

# Access the application
# Vue UI: http://localhost:3000
# API Gateway: http://localhost:8080
# Game Service: http://localhost:8081
```

**Docker Services**:
1. `mongodb` - Document database (port 27017)
2. `postgres` - Relational database (port 5432)
3. `game-service` - Scala microservice (port 8081)
4. `api-gateway` - HTTP gateway (port 8080)
5. `vue-ui` - Vue 3 SPA served by Nginx (port 3000)

## Component Details

### ChessBoard.vue

**Responsibilities**:
- Render 8x8 grid of squares
- Display chess pieces using Unicode symbols
- Handle square click events
- Highlight selected square and legal moves
- Show last move

**Piece Symbols**:
```
White: ♔ ♕ ♖ ♗ ♘ ♙
Black: ♚ ♛ ♜ ♝ ♞ ♟
```

**CSS Classes** (from `style.css`):
- `.chess-board` - Grid layout, border, shadow
- `.chess-square` - Individual square styling
- `.light` / `.dark` - Alternating square colors (amber)
- `.selected` - Blue ring for selected piece
- `.legal-move` - Green dot overlay
- `.last-move` - Yellow highlight

### GameControls.vue

**Features**:
- **New Game** button - Creates fresh game via API
- **Reset** button - Clears local state
- **Status Display**:
  - Current turn (White/Black)
  - Check warning
  - Checkmate announcement
  - Stalemate/Draw messages
- **Error Display** - Shows API errors

### MoveHistory.vue

**Features**:
- Scrollable move list
- Standard algebraic notation (SAN)
- Move numbering (1. e4 e5 2. Nf3 ...)
- PGN export display
- Auto-scroll to latest move

### ThemeToggle.vue

**Features**:
- Toggle button (🌙/☀️ icons)
- ARIA label for accessibility
- Smooth color transitions
- Persists preference to localStorage

## Responsive Design Patterns

### Tailwind Breakpoints

```javascript
// tailwind.config.js
screens: {
  'mobile': {'max': '767px'},
  'tablet': {'min': '768px', 'max': '1023px'},
  'desktop': {'min': '1024px'},
}
```

### Layout Strategy

**Mobile** - Vertical stacking:
```vue
<div class="grid grid-cols-1">
  <ChessBoard />
  <GameControls />
  <MoveHistory />
</div>
```

**Desktop** - Sidebar layout:
```vue
<div class="grid grid-cols-1 lg:grid-cols-3">
  <div class="lg:col-span-2">
    <ChessBoard />
  </div>
  <div>
    <GameControls />
    <MoveHistory />
  </div>
</div>
```

## Accessibility Features

1. **ARIA Labels**: All interactive elements have descriptive labels
2. **Keyboard Navigation**: Buttons are keyboard-accessible (Tab, Enter)
3. **Focus Indicators**: Visible focus rings on controls
4. **Color Contrast**: WCAG AA compliant color combinations
5. **Semantic HTML**: Proper heading hierarchy and structure

## Testing Strategy

### Unit Tests (Vitest)

```bash
npm run test
```

**Test Coverage**:
- Pinia store actions and mutations
- API client mocking
- Component rendering

### E2E Tests (Playwright)

```bash
npm run test:e2e
```

**Test Scenarios**:
- Create new game
- Make valid moves
- Invalid move handling
- Checkmate detection
- Theme toggle
- Responsive layout

## Production Deployment

### Docker Build

```dockerfile
# Multi-stage build
# Stage 1: Build Vue app with Node
FROM node:20-alpine AS builder
RUN npm ci && npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

**Benefits**:
- Small final image (~25 MB)
- Fast static asset serving
- SPA routing fallback configured
- Gzip compression enabled
- Security headers included

### Nginx Configuration

```nginx
# SPA fallback
location / {
  try_files $uri $uri/ /index.html;
}

# Static asset caching
location ~* \.(js|css|png|jpg|svg)$ {
  expires 1y;
  add_header Cache-Control "public, immutable";
}
```

## Performance Optimizations

1. **Vite Build**:
   - Code splitting
   - Tree shaking
   - Minification
   - Source maps (dev only)

2. **Asset Optimization**:
   - SVG icons (no external fonts)
   - Unicode chess pieces (no image sprites)
   - Gzip compression

3. **Lazy Loading**:
   - Route-based code splitting (future enhancement)
   - Dynamic component imports

4. **Caching Strategy**:
   - Long-term cache for static assets (1 year)
   - Service Worker support (future enhancement)

## Comparison to Lichess

**Inspirations Taken**:
- Clean board design with subtle colors
- Highlighting patterns (selected, legal, last move)
- Move history in algebraic notation
- Responsive layout strategies

**Differences**:
- Simpler UI (educational focus)
- No analysis engine
- No multiplayer (yet)
- Single game mode

## Success Criteria ✅

- [x] Vue 3 + TypeScript project initialized
- [x] Tailwind CSS with responsive breakpoints
- [x] Pinia state management (game, ui)
- [x] Chess board with click-to-move interaction
- [x] Mobile (<768px), Tablet (768-1024px), Desktop (>1024px) layouts
- [x] Dark/light theme toggle
- [x] Game Service API integration
- [x] Move history and PGN display
- [x] ARIA labels for accessibility
- [x] Docker build with Nginx
- [x] Fast load time (<2s on localhost)
- [x] Clean, maintainable code structure

## Future Enhancements

1. **Drag-and-Drop**: Mouse drag for piece movement
2. **Touch Gestures**: Swipe support for mobile
3. **Sound Effects**: Move, capture, check sounds
4. **Animations**: Piece movement transitions
5. **Opening Suggestions**: Integration with opening database
6. **Game Analysis**: Evaluation bar and best moves
7. **Multiplayer**: WebSocket support for live games
8. **Puzzle Mode**: Tactical training interface
9. **User Accounts**: Save games, track rating
10. **i18n**: Multi-language support

## Running the Application

### Quick Start

```bash
# 1. Start all services with Docker Compose
docker-compose up --build

# 2. Open browser to http://localhost:3000

# 3. Click "New Game" to start playing

# 4. Click pieces to select, then click destination square
```

### Development Mode (Frontend Only)

```bash
# Terminal 1: Start backend services
docker-compose up mongodb postgres game-service api-gateway

# Terminal 2: Start Vue dev server
cd frontend
npm install
npm run dev

# Open http://localhost:5173
```

## Troubleshooting

**Issue**: Board not loading
- Check API Gateway is running: `curl http://localhost:8080/health`
- Verify CORS settings in backend
- Check browser console for errors

**Issue**: Moves not working
- Ensure Game Service is healthy: `docker-compose ps`
- Check network tab in DevTools
- Verify Chess.js is installed: `npm list chess.js`

**Issue**: Theme not persisting
- Check localStorage in DevTools
- Verify browser supports localStorage
- Clear cache and reload

## References

- [Vue 3 Documentation](https://vuejs.org/)
- [Pinia Documentation](https://pinia.vuejs.org/)
- [Tailwind CSS](https://tailwindcss.com/)
- [Chess.js](https://github.com/jhlywa/chess.js)
- [Vite](https://vitejs.dev/)
- [Lichess Open Source](https://github.com/lichess-org/lila)

## Summary

Lecture 8 demonstrates modern front-end development practices:
- **Vue 3 Composition API** for reactive component architecture
- **TypeScript** for type safety and better developer experience
- **Tailwind CSS** for utility-first responsive styling
- **Pinia** for centralized state management
- **Docker** for consistent deployment

The application is production-ready, responsive, and provides an excellent foundation for future enhancements like multiplayer, analysis, and advanced features.
