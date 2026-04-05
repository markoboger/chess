package chess.util

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ObserverSpec extends AnyWordSpec with Matchers {

  class TestObservable extends Observable[String]
  class TestObserver extends Observer[String] {
    var events: List[String] = Nil
    override def update(event: String): Unit = events = events :+ event
  }

  "Observable" should {
    "notify a single observer" in {
      val observable = new TestObservable
      val observer = new TestObserver
      observable.add(observer)

      observable.notifyObservers("hello")

      observer.events shouldBe List("hello")
    }

    "notify multiple observers" in {
      val observable = new TestObservable
      val obs1 = new TestObserver
      val obs2 = new TestObserver
      observable.add(obs1)
      observable.add(obs2)

      observable.notifyObservers("event")

      obs1.events shouldBe List("event")
      obs2.events shouldBe List("event")
    }

    "stop notifying a removed observer" in {
      val observable = new TestObservable
      val observer = new TestObserver
      observable.add(observer)

      observable.notifyObservers("before")
      observable.remove(observer)
      observable.notifyObservers("after")

      observer.events shouldBe List("before")
    }

    "not fail when removing an observer that was never added" in {
      val observable = new TestObservable
      val observer = new TestObserver
      observable.remove(observer) // should not throw
    }

    "not fail when notifying with no observers" in {
      val observable = new TestObservable
      observable.notifyObservers("no one listening") // should not throw
    }
  }
}
