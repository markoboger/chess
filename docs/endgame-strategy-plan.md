# Endgame Strategy Plan

## Goal

Reduce the large number of drawn computer-vs-computer games by improving how the engine evaluates and converts simplified positions.

The current search is already reasonably fast and tactically acceptable for short horizons. The weakest area is endgame conversion:

- kings stay too passive
- passed pawns are undervalued
- favorable simplifications are not rewarded enough
- repetition is often acceptable even when one side is clearly better

This work is split into slices so we can improve strength incrementally without destabilizing the current engine.

## Slice 1: Phase-Aware Evaluation

Goal:

- detect middlegame vs endgame
- switch king evaluation in endgames from "safety first" to "activity first"
- reward passed pawns in endgames

Deliverables:

- `Evaluator.phase(board)` with a simple, explainable phase model
- dedicated endgame king table
- passed-pawn bonus used only in endgames
- tests proving:
  - phase detection works on simple positions
  - kings are rewarded for centralization in endgames
  - passed pawns score higher than blocked/non-passed pawns

Why first:

- highest strength-per-effort improvement
- no API break
- all search strategies benefit immediately because they already use `Evaluator`

## Slice 2: Better Endgame Quiescence

Goal:

- extend leaf stabilization beyond captures

Deliverables:

- quiescence search also considers:
  - promotions
  - checking moves
- tests for promotion races and checking endgames

Why next:

- many endgames hinge on checks and promotion threats, not just captures

## Slice 3: Draw Avoidance When Ahead

Goal:

- stop strong positions from drifting into unnecessary repetitions

Deliverables:

- evaluation penalty for repetition/stagnation when the side to move is clearly better
- more tolerance for drawing lines when behind
- tests covering favorable vs unfavorable repetition choices

## Slice 4: Conversion Heuristics

Goal:

- make the engine choose more practical winning plans

Deliverables:

- encourage:
  - king centralization in low-material positions
  - exchanges when materially ahead
  - rook behind passed pawns
  - preserving winning pawn majorities
- tests on simple technical endgames

## Slice 5: Endgame-Aware Strategy Variant

Goal:

- expose the improvement as a named stronger strategy for comparisons

Deliverables:

- `endgame-aware-minimax` or similar strategy label
- match-runner experiments comparing old vs new strategy
- documentation of measurable improvement

## Evaluation Approach

We will validate improvements with the new `match-runner-service`:

- old strategy vs new strategy
- mirrored colors
- repeated games
- stored results in PostgreSQL

Primary success metric:

- more decisive results against weaker strategies

Secondary success metrics:

- shorter conversion from winning positions
- fewer unnecessary repetitions
- no noticeable regressions in stability
