package part4_advanced_clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, ReceiveTimeout}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.typesafe.config.ConfigFactory

import java.util.UUID
import scala.util.Random
import scala.concurrent.duration.{Duration, DurationInt}
import scala.io.Source

case class Person(id: String, age: Int)
object Person {
  def generate(): Person = Person(UUID.randomUUID().toString, 16 + Random.nextInt(90))
}

case class Vote(person: Person, candidate: String)
case object VoteAccepted
case class VoteRejected(reason: String)

/**
 * VotingAggregator is stateful actor
 * so, it's state will lose if node goes down.
 *
 * Solution: Make VotingAggregator a persistent actor and save state
 * to distributed db like Cassandra. This data would be read if
 * singleton node is changed.
 */
class VotingAggregator extends Actor with ActorLogging {
  val CANDIDATES = Set("Martin", "Roland", "Jonas", "Daniel")

  override def receive: Receive = online(Set(), Map())

  context.setReceiveTimeout(100 seconds)
  def online(personsVoted: Set[String], polls: Map[String, Int]): Receive = {
    case Vote(Person(id, age), candidate) =>
      if(personsVoted.contains(id)) sender ! VoteRejected("already voted")
      else if(age < 18) sender() ! VoteRejected("Not legal to vote")
      else if(!CANDIDATES.contains(candidate)) sender ! VoteRejected("Invalid candidate")
      else {
        log.info(s"Recording vote from person: ${id} for ${candidate}")
        val candidateVotes = polls.getOrElse(candidate, 0)
        sender() ! VoteAccepted
        context.become(online(personsVoted + id, polls + (candidate -> (candidateVotes + 1))))
      }

    case ReceiveTimeout =>
      log.info(s"TIME's up, here are the poll results: ${polls}")
      context.setReceiveTimeout(Duration.Undefined)
      context.become(offline)
  }

  def offline: Receive = {
    case v: Vote =>
      log.warning(s"Received ${v} which is invalid as the time is up")
      sender ! VoteRejected("cann't accept vote after poll closed")
    case m =>
      log.info(s"Received ${m} - won't process more messages after polls are closed")
  }
}

class VotingStation(votingAggregator: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case v: Vote => votingAggregator ! v
    case VoteAccepted => log.info("Vote was accepted")
    case VoteRejected(reason) => log.info(s"Vote was rejected: ${reason}")
  }
}

object VotingStation {
  def props(votingAggregator: ActorRef): Props = Props(new VotingStation(votingAggregator))
}

object CentralElectionSystem extends App {
  def startNode(port: Int) = {
    val config = ConfigFactory.parseString(
      s"""
        |akka.remote.artery.canonical.port = ${port}
        |""".stripMargin)
      .withFallback(ConfigFactory.load("part4_advanced_clustering/votingSystemSingleton.conf"))

    val system = ActorSystem("RTJVMCluster", config)

    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props[VotingAggregator],
        terminationMessage = PoisonPill,
        ClusterSingletonManagerSettings(system)
      ), "votingAggregator"
    )
  }

  (2551 to 2553).foreach(startNode)
}

class VotingStationApp(port: Int) extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = ${port}
       |""".stripMargin)
    .withFallback(ConfigFactory.load("part4_advanced_clustering/votingSystemSingleton.conf"))

  val system = ActorSystem("RTJVMCluster", config)

  val proxy = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/votingAggregator",
      settings = ClusterSingletonProxySettings(system)
    ), "votingStationProxy"
  )

  val votingStation = system.actorOf(VotingStation.props(proxy), "votingStation")

  Source.stdin.getLines().foreach { name =>
    votingStation ! Vote(Person.generate(), name)
  }
}

object Gujarat extends VotingStationApp(2561)
object UttarPradesh extends VotingStationApp(2562)
object Maharashtra extends VotingStationApp(2563)