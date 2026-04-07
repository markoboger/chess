package chess.aview

/** Prevents WebSocket-delivered session moves from being echoed back to the backend.
  *
  * The GUI observes both local moves and remote session moves through the same controller
  * observer path. Local moves should be published to the backend; remote moves should not.
  * This gate suppresses exactly one publish for each remote move that was applied locally.
  */
private[aview] final class SessionMovePublicationGate:
  private var pendingRemoteMoves: Int = 0

  def markRemoteMoveApplied(): Unit = synchronized {
    pendingRemoteMoves += 1
  }

  def shouldPublishObservedMove(): Boolean = synchronized {
    if pendingRemoteMoves > 0 then
      pendingRemoteMoves -= 1
      false
    else true
  }

  def pendingRemoteMoveCount: Int = synchronized {
    pendingRemoteMoves
  }
