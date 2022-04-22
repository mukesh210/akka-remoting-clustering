package part4_advanced_clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.typesafe.config.ConfigFactory

import java.util.{Date, UUID}
import scala.collection.immutable
import scala.util.Random
import scala.concurrent.duration._

case class OysterCard(id: String, amount: Double)
case class EntryAttempt(oysterCard: OysterCard, date: Date)
case object EntryAccepted
case class EntryRejected(reason: String)
// passivate message
case object TerminateValidator

/////////////////////////
// Actors
////////////////////////
class Turnstile(validator: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = {
    case o: OysterCard => validator ! EntryAttempt(o, new Date)
    case EntryAccepted => log.info("GREEN: Please pass")
    case EntryRejected(reason) => log.info(s"RED: ${reason}")
  }
}

object Turnstile {
  def props(actorRef: ActorRef) = Props(new Turnstile(actorRef))
}

class OysterCardValidator extends Actor with ActorLogging {
  /*
  Store an enormous amount of data
   */
  override def preStart(): Unit = {
    super.preStart()
    log.info("Validator starting...")
    context.setReceiveTimeout(10 seconds)
  }

  override def receive: Receive = {
    case EntryAttempt(card @ OysterCard(id, amount), _) =>
      log.info(s"Validating ${card}")
      if(amount > 2.5) sender() ! EntryAccepted
      else sender() ! EntryRejected(s"[${id}] not enough funds, please top up")
    case ReceiveTimeout =>
      context.parent ! Passivate(TerminateValidator)
    case TerminateValidator =>
      // I am sure that I won't be contacted again so safe to stop
      context.stop(self)
  }
}

/////////////////////////
// Sharding Settings
////////////////////////
object TurnstileSettings {
  val numberOfShards = 10 // use 10x the number of nodes in your cluster
  val numberOfEntities = 100 // 10x number of shards

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case attempt @ EntryAttempt(OysterCard(cardId, _), _) =>
      val entityId = cardId.hashCode.abs % numberOfEntities
      (entityId.toString, attempt)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntryAttempt(OysterCard(cardId, _), _) =>
      val shardId = cardId.hashCode.abs % numberOfShards
      shardId.toString

      //below is part to remember entity so that entity will be automatically created on new node
    // when a node dies
    case ShardRegion.StartEntity(entityId) =>
      (entityId.toLong % numberOfShards).toString
  }

  /*
    M -> extractEntityId 43
      -> extractShardId 9

    everytime in future, if entityId is 43, then shardId should also be 9

    There must be NO two messages M1 and M2 for which
    extractEntityId(M1) == extractEntityId(M2) and
    extractShardId(M1) != extractShardId(M2)

    IF we fail to do above"
    M1 -> E37, S9
    M2 -> E37, S10
    then S9 and S10 both will create E37 on their shards. It will be bad if
    Entity is persistent actor.

    For (ShardRegion.StartEntity(entityId))
    If entityId -> shardId, then FORALL messages M, if extractEntityId(M) = entityId,
    then extractShardId(M) MUST BE shardId
   */
}

/////////////////////////
// Cluster nodes
////////////////////////
class TubeStation(port: Int, numberOfTurnstiles: Int) extends App {
  val config = ConfigFactory.parseString(
    s"""
      |akka.remote.artery.canonical.port = ${port}
      |""".stripMargin)
    .withFallback(ConfigFactory.load("part4_advanced_clustering/clusterShardingExample.conf"))

  val system = ActorSystem("RTJVMCluster", config)

  // Setting up Cluster Sharding
  val validatorShardRegionRef = ClusterSharding(system).start(
    typeName = "OysterCardValidator",
    entityProps = Props[OysterCardValidator],
    settings = ClusterShardingSettings(system).withRememberEntities(true),
    extractEntityId = TurnstileSettings.extractEntityId,
    extractShardId = TurnstileSettings.extractShardId
  )

  val turnstiles: immutable.Seq[ActorRef] = (1 to numberOfTurnstiles).map(_ => system.actorOf(Turnstile.props(validatorShardRegionRef)))

  Thread.sleep(10000)
  for( _ <- 1 to 1000) {
    val randomTurnstileIndex = Random.nextInt(numberOfTurnstiles)
    val randomTurnstile = turnstiles(randomTurnstileIndex)

    randomTurnstile ! OysterCard(UUID.randomUUID().toString, Random.nextDouble() * 10)
    Thread.sleep(200)
  }
}

object PiccadillyCircus extends TubeStation(2551, 10)
object Westminster extends TubeStation(2561, 5)
object CharingCross extends TubeStation(2571, 15)