package chess.controller.strategy

import chess.controller.{ComputerPlayer, MoveStrategy}
import chess.controller.io.fen.RegexFenParser
import chess.model.{Board, Color, File, Piece, PromotableRole, Rank, Role, Square}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class StrategySpec extends AnyWordSpec with Matchers:

  // ── helpers ──────────────────────────────────────────────────────────────

  private def fen(s: String): Board = RegexFenParser.parseFEN(s).get

  /** Position where White has a hanging queen on d5 — Black queen on d8 can capture it with no pawns blocking the
    * d-file. Kings on e1/e8.
    */
  private val hangingQueenPos =
    fen("3qk3/8/8/3Q4/8/8/8/4K3")

  /** White: King g6, Queen g5. Black: King g8 — Qg7# is the only checkmate move. */
  private val mateIn1 = fen("6k1/8/6K1/6Q1/8/8/8/8")

  /** White can mate but Black can also threaten; good for depth-3 traversal. */
  private val endgamePos = fen("6k1/5ppp/8/8/8/8/5PPP/R5K1")

  // ── Evaluator ─────────────────────────────────────────────────────────────

  "Evaluator.evaluate" should {
    "return 0 for the initial symmetric position" in {
      Evaluator.evaluate(Board.initial, Color.White) shouldBe 0
    }

    "be negated when color flips on the initial position" in {
      Evaluator.evaluate(Board.initial, Color.White) shouldBe
        -Evaluator.evaluate(Board.initial, Color.Black)
    }

    "return positive score when White has extra material" in {
      // White has an extra queen hanging on d5
      Evaluator.evaluate(hangingQueenPos, Color.White) should be > 0
    }
  }

  "Evaluator.pstBonus" should {
    "return the pawn table value for a white pawn on e4" in {
      val sq = Square(File.E, Rank._4)
      val bonus = Evaluator.pstBonus(Role.Pawn, sq, Color.White)
      bonus shouldBe 20 // centre bonus in the pawn table
    }

    "mirror for Black: pawn on e5 should equal white pawn on e4" in {
      val e4 = Square(File.E, Rank._4)
      val e5 = Square(File.E, Rank._5)
      Evaluator.pstBonus(Role.Pawn, e4, Color.White) shouldBe
        Evaluator.pstBonus(Role.Pawn, e5, Color.Black)
    }

    "return knight table value" in {
      val sq = Square(File.D, Rank._4)
      val bonus = Evaluator.pstBonus(Role.Knight, sq, Color.White)
      bonus should be >= -50
    }

    "return bishop table value" in {
      val sq = Square(File.D, Rank._4)
      Evaluator.pstBonus(Role.Bishop, sq, Color.White) should be >= -20
    }

    "return rook table value" in {
      val sq = Square(File.D, Rank._7)
      Evaluator.pstBonus(Role.Rook, sq, Color.White) should be >= -5
    }

    "return queen table value" in {
      val sq = Square(File.D, Rank._5)
      Evaluator.pstBonus(Role.Queen, sq, Color.White) should be >= -20
    }

    "return king table value" in {
      val sq = Square(File.G, Rank._1)
      Evaluator.pstBonus(Role.King, sq, Color.White) should be >= -50
    }
  }

  "Evaluator.materialValue" should {
    "return expected centipawn values" in {
      Evaluator.materialValue(Role.Pawn) shouldBe 100
      Evaluator.materialValue(Role.Knight) shouldBe 320
      Evaluator.materialValue(Role.Bishop) shouldBe 330
      Evaluator.materialValue(Role.Rook) shouldBe 500
      Evaluator.materialValue(Role.Queen) shouldBe 900
      Evaluator.materialValue(Role.King) shouldBe 20000
    }
  }

  // ── MoveStrategy.promotionFor ─────────────────────────────────────────────

  "MoveStrategy.promotionFor" should {
    "return Some(Queen) when a white pawn reaches rank 8" in {
      val board = fen("8/4P3/8/8/8/8/8/8")
      val from = Square("e7")
      val to = Square("e8")
      MoveStrategy.promotionFor(board, from, to, Color.White) shouldBe Some(PromotableRole.Queen)
    }

    "return Some(Queen) when a black pawn reaches rank 1" in {
      val board = fen("8/8/8/8/8/8/4p3/8")
      val from = Square("e2")
      val to = Square("e1")
      MoveStrategy.promotionFor(board, from, to, Color.Black) shouldBe Some(PromotableRole.Queen)
    }

    "return None for a non-promoting pawn move" in {
      val from = Square("e2")
      val to = Square("e4")
      MoveStrategy.promotionFor(Board.initial, from, to, Color.White) shouldBe None
    }

    "return None when the square has no piece" in {
      val from = Square("e5")
      MoveStrategy.promotionFor(Board.initial, from, Square("e6"), Color.White) shouldBe None
    }
  }

  // ── ComputerPlayer ────────────────────────────────────────────────────────

  /** White Kb1, Qa1, Black Kh8: White is up a queen (900 cp ≥ 150 threshold). */
  private val aheadBoard = fen("7k/8/8/8/8/8/8/QK6")

  "ComputerPlayer" should {
    "delegate to its strategy" in {
      val player = new ComputerPlayer(new RandomStrategy)
      val result = player.move(Board.initial, Color.White)
      result shouldBe defined
    }

    "use RandomStrategy by default" in {
      val player = new ComputerPlayer()
      player.strategy shouldBe a[RandomStrategy]
    }

    "allow strategy to be swapped at runtime" in {
      val player = new ComputerPlayer(new RandomStrategy)
      player.strategy = new GreedyStrategy
      player.strategy shouldBe a[GreedyStrategy]
    }

    "return None when there are no legal moves" in {
      // Stalemate: White king cornered with no moves
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      val player = new ComputerPlayer(new RandomStrategy)
      player.move(stalemateBoard, Color.Black) shouldBe None
    }

    "not trigger repetition avoidance when not ahead in material" in {
      // Initial board: material balance is 0 (< 150) — avoidance never triggers
      // Even with wouldRepeat = true, the candidate is returned unchanged
      val player = new ComputerPlayer(new RandomStrategy)
      player.move(Board.initial, Color.White, _ => true) shouldBe defined
    }

    "return candidate when ahead but candidate does not repeat" in {
      val player = new ComputerPlayer(new RandomStrategy)
      // wouldRepeat = false for everything → candidateRepeats = false → return candidate
      player.move(aheadBoard, Color.White, _ => false) shouldBe defined
    }

    "fall back to candidate when ahead but all moves repeat" in {
      val player = new ComputerPlayer(new RandomStrategy)
      // wouldRepeat = true for everything → alternatives empty → accept draw, return candidate
      player.move(aheadBoard, Color.White, _ => true) shouldBe defined
    }

    "choose a non-repeating alternative when ahead and candidate repeats" in {
      // Use GreedyStrategy so the candidate is deterministic (captures best piece)
      val player = new ComputerPlayer(new GreedyStrategy)
      // Identify what GreedyStrategy picks so we can mark it as repeating
      val candidateOpt = new GreedyStrategy().selectMove(aheadBoard, Color.White)
      candidateOpt shouldBe defined
      val (cf, ct, cp) = candidateOpt.get
      val candidateBoard = aheadBoard.move(cf, ct, cp).toOption
      candidateBoard shouldBe defined
      // Mark only the candidate's resulting board as "repeating"
      val wouldRepeat: Board => Boolean = b => b == candidateBoard.get
      // ComputerPlayer must now find a non-repeating alternative
      val result = player.move(aheadBoard, Color.White, wouldRepeat)
      result shouldBe defined
    }
  }

  // ── RandomStrategy ────────────────────────────────────────────────────────

  "RandomStrategy" should {
    "always return a legal move from the initial position" in {
      val s = new RandomStrategy
      s.name shouldBe "Random"
      val result = s.selectMove(Board.initial, Color.White)
      result shouldBe defined
    }

    "return None when no legal moves exist" in {
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      new RandomStrategy().selectMove(stalemateBoard, Color.Black) shouldBe None
    }
  }

  // ── GreedyStrategy ────────────────────────────────────────────────────────

  /** White queen on d1 can capture a black piece on d5 (no blocking pieces) */
  private val rookOnD5 = fen("4k3/8/8/3r4/8/8/8/3QK3")
  private val bishopOnD5 = fen("4k3/8/8/3b4/8/8/8/3QK3")
  private val pawnOnD5 = fen("4k3/8/8/3p4/8/8/8/3QK3")

  "GreedyStrategy" should {
    "have the correct name" in {
      new GreedyStrategy().name shouldBe "Greedy"
    }

    "capture a hanging queen when available" in {
      val s = new GreedyStrategy
      val result = s.selectMove(hangingQueenPos, Color.Black)
      result shouldBe defined
      // Should move to d5 to capture the queen
      result.get._2 shouldBe Square("d5")
    }

    "prefer a rook capture over quiet moves" in {
      val s = new GreedyStrategy
      val result = s.selectMove(rookOnD5, Color.White)
      result shouldBe defined
      result.get._2 shouldBe Square("d5")
    }

    "prefer a bishop capture over quiet moves" in {
      val s = new GreedyStrategy
      val result = s.selectMove(bishopOnD5, Color.White)
      result shouldBe defined
      result.get._2 shouldBe Square("d5")
    }

    "prefer a pawn capture over quiet moves" in {
      val s = new GreedyStrategy
      val result = s.selectMove(pawnOnD5, Color.White)
      result shouldBe defined
      result.get._2 shouldBe Square("d5")
    }

    "return a move when only quiet moves are available" in {
      val s = new GreedyStrategy
      s.selectMove(Board.initial, Color.White) shouldBe defined
    }

    "return None when no legal moves exist" in {
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      new GreedyStrategy().selectMove(stalemateBoard, Color.Black) shouldBe None
    }
  }

  // ── MaterialBalanceStrategy ───────────────────────────────────────────────

  "MaterialBalanceStrategy" should {
    "have the correct name" in {
      new MaterialBalanceStrategy().name shouldBe "Material Balance"
    }

    "capture a hanging queen when available" in {
      val s = new MaterialBalanceStrategy
      val result = s.selectMove(hangingQueenPos, Color.Black)
      result shouldBe defined
      result.get._2 shouldBe Square("d5")
    }

    "return a move from the initial position" in {
      new MaterialBalanceStrategy().selectMove(Board.initial, Color.White) shouldBe defined
    }

    "return None when no legal moves exist" in {
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      new MaterialBalanceStrategy().selectMove(stalemateBoard, Color.Black) shouldBe None
    }
  }

  // ── PieceSquareStrategy ───────────────────────────────────────────────────

  "PieceSquareStrategy" should {
    "have the correct name" in {
      new PieceSquareStrategy().name shouldBe "Piece-Square Tables"
    }

    "return a move from the initial position" in {
      new PieceSquareStrategy().selectMove(Board.initial, Color.White) shouldBe defined
    }

    "return None when no legal moves exist" in {
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      new PieceSquareStrategy().selectMove(stalemateBoard, Color.Black) shouldBe None
    }
  }

  // ── MinimaxStrategy ───────────────────────────────────────────────────────

  "MinimaxStrategy" should {
    "have the correct name" in {
      new MinimaxStrategy(3).name shouldBe "Minimax (d=3)"
    }

    "use default depth 3" in {
      new MinimaxStrategy().depth shouldBe 3
    }

    "return a move from the initial position" in {
      new MinimaxStrategy(2).selectMove(Board.initial, Color.White) shouldBe defined
    }

    "return None when no legal moves exist" in {
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      new MinimaxStrategy(2).selectMove(stalemateBoard, Color.Black) shouldBe None
    }

    "find a winning move when available (depth 2)" in {
      val s = new MinimaxStrategy(2)
      val result = s.selectMove(hangingQueenPos, Color.Black)
      result shouldBe defined
      result.get._2 shouldBe Square("d5")
    }

    "detect checkmate in 1 (exercises terminal-node path in alphaBeta)" in {
      // White: King g6, Queen g5. Black: King g8.
      // Both Qg7# and Qd8# are valid mates — just verify a move is found.
      val mateIn1 = fen("6k1/8/6K1/6Q1/8/8/8/8")
      val s = new MinimaxStrategy(2)
      s.selectMove(mateIn1, Color.White) shouldBe defined
    }

    "evaluate stalemate correctly in alphaBeta" in {
      // Position where best line leads to stalemate: White king c6, pawn c7, Black king a8.
      // After c8=Q+?, Black is stalemated. Minimax should avoid this (or at least not crash).
      val almostStalemate = fen("k7/2P5/2K5/8/8/8/8/8")
      val s = new MinimaxStrategy(2)
      s.selectMove(almostStalemate, Color.White) // should not throw; result can be anything
    }

    "cover maximizing=true recursive branch (depth 3)" in {
      // depth=3 causes alphaBeta to be called with maximizing=false (depth 2),
      // which recurses with maximizing=true (depth 1) — exercising that branch
      val s = new MinimaxStrategy(3)
      s.selectMove(endgamePos, Color.White) shouldBe defined
    }

    "score opponent checkmate in minimizing branch (depth 2)" in {
      // White Kh1 is in check from Black Rh2. White's only legal move is Ng4×h2.
      // After Ng4×h2, Black plays Rg2×h2# (checkmate). This exercises the
      // `case GameEvent.Checkmate => -INF + ...` branch in the minimizing half.
      val forceBlackMate = fen("7k/8/8/8/6N1/8/5qrr/7K")
      new MinimaxStrategy(2).selectMove(forceBlackMate, Color.White) shouldBe defined
    }
  }

  // ── QuiescenceStrategy ────────────────────────────────────────────────────

  "QuiescenceStrategy" should {
    "have the correct name" in {
      new QuiescenceStrategy(3, 6).name shouldBe "Minimax+QSearch (d=3)"
    }

    "use default depth and qDepth" in {
      val s = new QuiescenceStrategy()
      s.depth shouldBe 3
      s.qDepth shouldBe 6
    }

    "return a move from the initial position" in {
      new QuiescenceStrategy(2, 3).selectMove(Board.initial, Color.White) shouldBe defined
    }

    "return None when no legal moves exist" in {
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      new QuiescenceStrategy(2, 3).selectMove(stalemateBoard, Color.Black) shouldBe None
    }

    "capture a hanging queen (depth 2)" in {
      val s = new QuiescenceStrategy(2, 3)
      val result = s.selectMove(hangingQueenPos, Color.Black)
      result shouldBe defined
      result.get._2 shouldBe Square("d5")
    }

    "detect checkmate in 1 (exercises terminal-node path in alphaBeta)" in {
      // White: King g6, Queen g5. Black: King g8.
      // Both Qg7# and Qd8# are valid mates — just verify a move is found.
      val mateIn1 = fen("6k1/8/6K1/6Q1/8/8/8/8")
      val s = new QuiescenceStrategy(2, 3)
      s.selectMove(mateIn1, Color.White) shouldBe defined
    }

    "evaluate stalemate line in alphaBeta" in {
      val almostStalemate = fen("k7/2P5/2K5/8/8/8/8/8")
      new QuiescenceStrategy(2, 3).selectMove(almostStalemate, Color.White)
    }

    "cover maximizing=true recursive branch (depth 3)" in {
      val s = new QuiescenceStrategy(3, 4)
      s.selectMove(endgamePos, Color.White) shouldBe defined
    }
  }

  // ── IterativeDeepeningStrategy ────────────────────────────────────────────

  "IterativeDeepeningStrategy" should {
    "have the correct name" in {
      new IterativeDeepeningStrategy().name shouldBe "Iterative Deepening"
    }

    "use default time limit of 2000ms" in {
      new IterativeDeepeningStrategy().timeLimitMs shouldBe 2000L
    }

    "allow time limit to be changed" in {
      val s = new IterativeDeepeningStrategy(500L)
      s.timeLimitMs = 1000L
      s.timeLimitMs shouldBe 1000L
    }

    "return a legal move from the initial position" in {
      new IterativeDeepeningStrategy(500L).selectMove(Board.initial, Color.White) shouldBe defined
    }

    "return None when no legal moves exist" in {
      val stalemateBoard = fen("7k/5Q2/6K1/8/8/8/8/8")
      new IterativeDeepeningStrategy(500L).selectMove(stalemateBoard, Color.Black) shouldBe None
    }

    "capture a hanging queen (finds winning move)" in {
      val s = new IterativeDeepeningStrategy(500L)
      val result = s.selectMove(hangingQueenPos, Color.Black)
      result shouldBe defined
      result.get._2 shouldBe Square("d5")
    }

    "return a move even with a very short time limit (depth-0 fallback)" in {
      // 1ms forces immediate timeout after depth-0 fallback is set
      new IterativeDeepeningStrategy(1L).selectMove(Board.initial, Color.White) shouldBe defined
    }

    "detect checkmate in 1 at searchAtDepth level" in {
      // White: King g6, Queen g5. Black: King g8. Qg7# is mate in 1.
      val s = new IterativeDeepeningStrategy(500L)
      val result = s.selectMove(mateIn1, Color.White)
      result shouldBe defined
    }

    "handle stalemate at searchAtDepth level" in {
      // Position where White can stalemate Black — engine should not crash
      val almostStalemate = fen("k7/2P5/2K5/8/8/8/8/8")
      new IterativeDeepeningStrategy(500L).selectMove(almostStalemate, Color.White)
    }

    "exercise maximizing=true in alphaBeta (depth 3)" in {
      val s = new IterativeDeepeningStrategy(1000L)
      s.selectMove(endgamePos, Color.White) shouldBe defined
    }

    "score opponent checkmate in minimizing branch (depth 2)" in {
      // White Kh1 is in check from Black Rh2. White's only legal move is Ng4×h2.
      // After Ng4×h2, Black plays Rg2×h2# (checkmate). This exercises the
      // `case GameEvent.Checkmate => -INF + ...` branch in the minimizing half.
      val forceBlackMate = fen("7k/8/8/8/6N1/8/5qrr/7K")
      new IterativeDeepeningStrategy(500L).selectMove(forceBlackMate, Color.White) shouldBe defined
    }
  }
