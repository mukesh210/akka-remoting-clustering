package part1_recap

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, PoisonPill, Props, Stash, SupervisorStrategy}
import akka.util.Timeout

object AkkaRecap extends App {
  class SimpleActor extends Actor with Stash with ActorLogging {
    override def receive: Receive = {
      case message => println(s"I received: $message")
      case "change" => context.become(anotherHandler)
      case "stashThis" =>
        stash()
      case "change handler now" =>
        unstashAll()
        context.become(anotherHandler)
      case "createChild" =>
        val childActor = context.actorOf(Props[SimpleActor], "childActor")
        childActor ! "Hello"
    }

    def anotherHandler: Receive = {
      case message => println(s"In another receive handler: $message")
    }

    override def preStart(): Unit = {
      log.info("I am starting")
    }

    override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: RuntimeException => Restart
      case _ => Stop
    }
  }

  // actor encapsulation
  val system = ActorSystem("AkkaRecap")
  // new SimpleActor  // not allowed
  // #1: you can only instantiate actor through an actorsystem
  val actor = system.actorOf(Props[SimpleActor], "simpleActor")

  // #2: can only communicate through actor by sending messages
  actor ! "Hello"

  /*
    - messages are sent asynchronously
    - many actors in millions can share a few dozen threads
    - each message is processed/handled ATOMICALLY
    - no need to locking because each messages is processed atomically
   */

  // changing actor behavior + stashing
  // actors can spawn another actors
  // guardians: /system, /user, / = root guardian

  // actors have a defined lifecycle: they can be started, stopped, suspended, resumed and restarted
  // stopping actors - context.stop
  actor ! PoisonPill

  // logging
  // supervision

  // configure Akka infrastructure: dispatchers, routers, mailboxes

  // schedulers
  import scala.concurrent.duration._
  import system.dispatcher
  system.scheduler.scheduleOnce(2 seconds) {
    actor ! "Delayed Happy Birthday!"
  }

  // Akka patterns including FSM + ask pattern
  import akka.pattern.ask
  implicit val timeout: Timeout = Timeout(3 seconds)

  val future = actor ? "question"

  // the pipe pattern
  import akka.pattern.pipe
  val anotherActor = system.actorOf(Props[SimpleActor], "anotherSimpleActor")
  future.mapTo[String].pipeTo(anotherActor)
}
