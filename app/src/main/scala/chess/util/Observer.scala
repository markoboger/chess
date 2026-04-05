package chess.util

/** A simple observer that reacts to events of type `E`.
  *
  * Implement [[update]] to handle each event published by an [[Observable]].
  *
  * @tparam E
  *   the event type
  */
trait Observer[E]:
  def update(event: E): Unit

/** A subject that maintains a list of [[Observer]]s and notifies them of events.
  *
  * Mix this trait into any class that should broadcast events:
  * {{{
  * class GameController extends Observable[MoveResult]:
  *   def doSomething(): Unit =
  *     // ... perform action ...
  *     notifyObservers(result)
  * }}}
  *
  * @tparam E
  *   the event type
  */
trait Observable[E]:
  private var observers: Vector[Observer[E]] = Vector.empty

  def add(observer: Observer[E]): Unit =
    observers = observers :+ observer

  def remove(observer: Observer[E]): Unit =
    observers = observers.filterNot(_ eq observer)

  def notifyObservers(event: E): Unit =
    observers.foreach(_.update(event))
