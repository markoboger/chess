package chess.model

/** A monadic type representing the result of a chess move.
  *
  * `MoveResult` is either a successful [[MoveResult.Moved Moved]] containing the new
  * board state and a [[GameEvent]], or a [[MoveResult.Failed Failed]] containing the
  * unchanged board and a [[MoveError]] explaining why the move was rejected.
  *
  * Supports `map`, `flatMap`, and `foreach` for monadic composition,
  * enabling for-comprehensions that short-circuit on the first illegal move:
  * {{{
  * val result: MoveResult = for
  *   b1 <- Board.initial.move(Square("e2"), Square("e4"))
  *   b2 <- b1.move(Square("e7"), Square("e5"))
  *   b3 <- b2.move(Square("g1"), Square("f3"))
  * yield b3
  *
  * result match
  *   case MoveResult.Moved(board, event) => println(s"Event: $event")
  *   case MoveResult.Failed(_, error)    => println(s"Illegal: ${error.message}")
  * }}}
  */
sealed trait MoveResult:

  /** The board state — the new board on success, the unchanged board on failure. */
  def board: Board

  /** Chains a move-producing function. Short-circuits on failure.
    *
    * On [[MoveResult.Moved Moved]], applies `f` to the board producing the next `MoveResult`.
    * On [[MoveResult.Failed Failed]], propagates the failure unchanged.
    */
  def flatMap(f: Board => MoveResult): MoveResult

  /** Transforms the board inside a successful result. No effect on failure. */
  def map(f: Board => Board): MoveResult

  /** Executes a side effect with the board, only on success. */
  def foreach(f: Board => Unit): Unit

  /** True if this represents a successful move. */
  def isSuccess: Boolean

  /** True if this represents a rejected move. */
  def isFailed: Boolean

  /** Returns the board on success, or throws [[NoSuchElementException]] on failure. */
  def get: Board

  /** Returns the board on success, or the given default on failure. */
  def getOrElse(default: => Board): Board

  /** Converts to `Option[Board]` — `Some(board)` on success, `None` on failure. */
  def toOption: Option[Board]

  /** The game event if this is a successful move, `None` otherwise. */
  def event: Option[GameEvent]

object MoveResult:

  /** A successful move: contains the resulting board and the [[GameEvent]] it produced. */
  final case class Moved(board: Board, gameEvent: GameEvent = GameEvent.Moved)
      extends MoveResult:
    def flatMap(f: Board => MoveResult): MoveResult = f(board)
    def map(f: Board => Board): MoveResult = Moved(f(board), gameEvent)
    def foreach(f: Board => Unit): Unit = f(board)
    def isSuccess: Boolean = true
    def isFailed: Boolean = false
    def get: Board = board
    def getOrElse(default: => Board): Board = board
    def toOption: Option[Board] = Some(board)
    def event: Option[GameEvent] = Some(gameEvent)

  /** A rejected move: contains the unchanged board and the [[MoveError]] reason. */
  final case class Failed(board: Board, error: MoveError) extends MoveResult:
    def flatMap(f: Board => MoveResult): MoveResult = this
    def map(f: Board => Board): MoveResult = this
    def foreach(f: Board => Unit): Unit = ()
    def isSuccess: Boolean = false
    def isFailed: Boolean = true
    def get: Board =
      throw new NoSuchElementException(s"MoveResult.Failed: ${error.message}")
    def getOrElse(default: => Board): Board = default
    def toOption: Option[Board] = None
    def event: Option[GameEvent] = None
