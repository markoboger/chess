// Chess Application - Microservices Frontend
// Communicates with API Gateway at http://localhost:8080

const API_BASE = '/api';

let currentGameId = null;

// Chess piece Unicode characters
const PIECES = {
    'K': '♔', 'Q': '♕', 'R': '♖', 'B': '♗', 'N': '♘', 'P': '♙',
    'k': '♚', 'q': '♛', 'r': '♜', 'b': '♝', 'n': '♞', 'p': '♟'
};

// Initialize the application
document.addEventListener('DOMContentLoaded', () => {
    setupEventListeners();
    renderEmptyBoard();
});

function setupEventListeners() {
    document.getElementById('newGame').addEventListener('click', createNewGame);
    document.getElementById('makeMove').addEventListener('click', makeMove);
    document.getElementById('loadFen').addEventListener('click', loadFenPosition);
    document.getElementById('copyFen').addEventListener('click', copyFen);

    // Enter key support for move input
    document.getElementById('moveInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') makeMove();
    });

    // Enter key support for FEN input
    document.getElementById('fenInput').addEventListener('keypress', (e) => {
        if (e.key === 'Enter') loadFenPosition();
    });
}

async function createNewGame() {
    try {
        const response = await apiCall('POST', `${API_BASE}/games`, {});
        currentGameId = response.gameId;

        document.getElementById('gameId').textContent = `Game ID: ${currentGameId}`;
        document.getElementById('fenDisplay').value = response.fen;
        document.getElementById('status').textContent = 'In Progress';
        document.getElementById('lastEvent').textContent = '';
        document.getElementById('moveHistory').innerHTML = '';

        await refreshBoard();
        logInfo('New game created successfully');
    } catch (error) {
        logError('Failed to create game', error);
    }
}

async function makeMove() {
    if (!currentGameId) {
        alert('Please create a new game first');
        return;
    }

    const moveInput = document.getElementById('moveInput');
    const move = moveInput.value.trim();

    if (!move) {
        alert('Please enter a move');
        return;
    }

    try {
        const response = await apiCall('POST', `${API_BASE}/games/${currentGameId}/moves`, { move });

        if (response.success) {
            document.getElementById('fenDisplay').value = response.fen;
            moveInput.value = '';

            // Update event display
            const eventDisplay = document.getElementById('lastEvent');
            if (response.event) {
                eventDisplay.textContent = `Event: ${response.event}`;
                eventDisplay.className = `event-display ${response.event}`;

                if (response.event === 'checkmate') {
                    document.getElementById('status').textContent = 'Checkmate!';
                } else if (response.event === 'stalemate') {
                    document.getElementById('status').textContent = 'Stalemate';
                } else if (response.event === 'check') {
                    document.getElementById('status').textContent = 'Check!';
                }
            } else {
                eventDisplay.textContent = '';
                eventDisplay.className = 'event-display';
            }

            await refreshBoard();
            await refreshMoveHistory();
            logInfo(`Move executed: ${move}`);
        }
    } catch (error) {
        logError(`Failed to make move: ${move}`, error);
        alert(`Invalid move: ${error.message || error}`);
    }
}

async function loadFenPosition() {
    if (!currentGameId) {
        alert('Please create a new game first');
        return;
    }

    const fenInput = document.getElementById('fenInput');
    const fen = fenInput.value.trim();

    if (!fen) {
        alert('Please enter a FEN string');
        return;
    }

    try {
        const response = await apiCall('POST', `${API_BASE}/games/${currentGameId}/fen`, { fen });

        if (response.success) {
            document.getElementById('fenDisplay').value = response.fen;
            fenInput.value = '';

            await refreshBoard();
            logInfo('FEN position loaded');
        }
    } catch (error) {
        logError('Failed to load FEN', error);
        alert(`Invalid FEN: ${error.message || error}`);
    }
}

async function refreshBoard() {
    if (!currentGameId) return;

    try {
        const response = await apiCall('GET', `${API_BASE}/games/${currentGameId}/fen`);
        renderBoard(response.fen);
    } catch (error) {
        logError('Failed to refresh board', error);
    }
}

async function refreshMoveHistory() {
    if (!currentGameId) return;

    try {
        const response = await apiCall('GET', `${API_BASE}/games/${currentGameId}/moves`);
        const moveHistoryDiv = document.getElementById('moveHistory');

        moveHistoryDiv.innerHTML = response.moves.map((move, index) => {
            const moveNumber = Math.floor(index / 2) + 1;
            const isWhite = index % 2 === 0;
            return `<div class="move-item">${moveNumber}.${isWhite ? '' : '..'} ${move}</div>`;
        }).join('');
    } catch (error) {
        logError('Failed to refresh move history', error);
    }
}

function renderBoard(fen) {
    const board = document.getElementById('board');
    board.innerHTML = '';

    const position = fenToBoard(fen);

    for (let rank = 7; rank >= 0; rank--) {
        for (let file = 0; file < 8; file++) {
            const square = document.createElement('div');
            square.className = `square ${(rank + file) % 2 === 0 ? 'dark' : 'light'}`;

            const piece = position[rank][file];
            if (piece) {
                square.textContent = PIECES[piece] || piece;
            }

            board.appendChild(square);
        }
    }
}

function renderEmptyBoard() {
    const board = document.getElementById('board');
    board.innerHTML = '';

    for (let rank = 7; rank >= 0; rank--) {
        for (let file = 0; file < 8; file++) {
            const square = document.createElement('div');
            square.className = `square ${(rank + file) % 2 === 0 ? 'dark' : 'light'}`;
            board.appendChild(square);
        }
    }
}

function fenToBoard(fen) {
    const position = Array(8).fill(null).map(() => Array(8).fill(null));
    const ranks = fen.split(' ')[0].split('/');

    ranks.forEach((rank, rankIndex) => {
        let fileIndex = 0;
        for (let char of rank) {
            if (isNaN(char)) {
                position[7 - rankIndex][fileIndex] = char;
                fileIndex++;
            } else {
                fileIndex += parseInt(char);
            }
        }
    });

    return position;
}

function copyFen() {
    const fenDisplay = document.getElementById('fenDisplay');
    fenDisplay.select();
    document.execCommand('copy');
    logInfo('FEN copied to clipboard');
}

async function apiCall(method, url, body = null) {
    const startTime = Date.now();

    try {
        const options = {
            method,
            headers: {
                'Content-Type': 'application/json'
            }
        };

        if (body) {
            options.body = JSON.stringify(body);
        }

        const response = await fetch(url, options);
        const duration = Date.now() - startTime;

        logApiCall(method, url, response.status, duration);

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || `HTTP ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        const duration = Date.now() - startTime;
        logApiCall(method, url, 'ERROR', duration, error.message);
        throw error;
    }
}

function logApiCall(method, url, status, duration, error = null) {
    const logDiv = document.getElementById('apiLog');
    const timestamp = new Date().toLocaleTimeString();

    const entry = document.createElement('div');
    entry.className = 'log-entry';
    entry.innerHTML = `
        <span class="timestamp">${timestamp}</span>
        <span class="method">${method}</span>
        <span class="url">${url}</span>
        <span class="status">${status}</span>
        <span class="duration">(${duration}ms)</span>
        ${error ? `<div style="color: #fc8181;">Error: ${error}</div>` : ''}
    `;

    logDiv.insertBefore(entry, logDiv.firstChild);

    // Keep only last 20 entries
    while (logDiv.children.length > 20) {
        logDiv.removeChild(logDiv.lastChild);
    }
}

function logInfo(message) {
    console.log(`[INFO] ${message}`);
}

function logError(message, error) {
    console.error(`[ERROR] ${message}`, error);
}
