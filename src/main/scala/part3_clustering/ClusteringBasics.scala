package part3_clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberJoined, MemberRemoved, MemberUp, UnreachableMember}
import com.typesafe.config.ConfigFactory

/**
 *
 * ActorSystem name should be same for nodes to be in same Cluster
 */
class ClusterSubscriber extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  // If ClusterSubscriber is started from actorSystem of cluster, then that actorSystem
  // will notify this actor of "MemberEvent" and "UnreachableMember"
  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    ) // can add as many events as we want
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case MemberJoined(member) =>
      log.info(s"New member in town: ${member.address}")

      // use memberRole for distributed computations
    case MemberUp(member) if member.hasRole("numberCruncher") =>
      log.info(s"HELLO BROTHER: ${member.address}")

    case MemberUp(member) =>
      log.info(s"Let's say Welcome to the newest member: ${member.address}")

    case MemberRemoved(member, previousStatus) =>
      log.info(s"Poor ${member.address}, it was removed from ${previousStatus}")

    case UnreachableMember(member) =>
      log.info(s"Uh oh, member ${member.address} is unreachable")

    case m: MemberEvent =>
      log.info(s"Another member event ${m}")
  }
}

object ClusteringBasics extends App {
  def startCluster(ports: List[Int]): Unit = {
    ports.foreach{port =>
      val config = ConfigFactory.parseString(
        s"""
          |akka.remote.artery.canonical.port = $port
          |""".stripMargin).withFallback(ConfigFactory.load("part3_clustering/clusteringBasics.conf"))

      val system = ActorSystem("RTJVMCluster", config) // all the actorSystems in a cluster must have same name
      system.actorOf(Props[ClusterSubscriber], "clusterSubscriber")
    }
  }

  // port 0 means system will allocate a random port to node
  startCluster(List(2551, 2552, 0))
}

// if seed node is not present in config, we can programatically register node in existing running cluster
object ClusteringBasics_ManualRegistration extends App {
  val system = ActorSystem(
    "RTJVMCluster", // name of actorSystem should be same as cluster we want to join
    ConfigFactory.load("part3_clustering/clusteringBasics.conf")
      .getConfig("manualRegistration")
  )

  val cluster = Cluster(system) // or Cluster.get(system)

  def joinExistingCluster =
  cluster.joinSeedNodes(List(
    Address("akka", "RTJVMCluster", "localhost", 2551), // akka://RTJVMCluster@localhost:2551
    Address("akka", "RTJVMCluster", "localhost", 2552)  // AddressFromURIString
  ))

  // it's not necessary to join seed node. New node can mention any active node in cluster
  // it's only initial contact point, later all comes in sync with gossip protocol
  def joinExistingNode =
  cluster.join(Address("akka", "RTJVMCluster", "localhost", 64074))


  // this way node will join itself, become leader and will be single node in it's own cluster
  def joinMyself =
    cluster.join(Address("akka", "RTJVMCluster", "localhost", 2555))

  joinExistingCluster
  system.actorOf(Props[ClusterSubscriber], "clusterSubscriber")
}
