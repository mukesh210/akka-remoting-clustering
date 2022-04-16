package part3_clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

/**
 * Pool Routers: create their own children
 * Group Routers: Fetch already created actors and use them as routees
 *
 */
case class SimpleTask(contents: String)
case object StartWork

class MasterWithRouter extends Actor with ActorLogging {
  val router = context.actorOf(
    FromConfig.props(Props[SimpleRoutee]),
    "clusterAwareRouter"
  )
  override def receive: Receive = {
    case StartWork =>
      log.info("Started Work")
      (1 to 100).foreach { id =>
        router ! SimpleTask(s"Simple task: ${id}")
      }
  }
}

class SimpleRoutee extends Actor with ActorLogging {
  override def receive: Receive = {
    case SimpleTask(contents) =>
      log.info(s"Processing ${contents}")
  }
}

// for round-robin-pool
object RouteesApp extends App {
  def startRouteeNode(port: Int) = {
    val config = ConfigFactory.parseString(
      s"""
        |akka.remote.artery.canonical.port = ${port}
        |""".stripMargin)
      .withFallback(ConfigFactory.load("part3_clustering/clusterAwareRouters.conf"))

    val system = ActorSystem("RTJVMCluster", config)
  }

  startRouteeNode(2551)
  startRouteeNode(2552)
}

object MasterWithRouterApp extends App {
  val mainConfig = ConfigFactory.load("part3_clustering/clusterAwareRouters.conf")
  val config = mainConfig.getConfig("masterWithRouterApp").withFallback(mainConfig)

  val system = ActorSystem("RTJVMCluster", config)
  val masterActor = system.actorOf(Props[MasterWithRouter], "master")

  Thread.sleep(10000)
  masterActor ! StartWork
}

// for round-robin-group
object RouteesAppWithRoundRobinGroup extends App {
  def startRouteeNode(port: Int) = {
    val config = ConfigFactory.parseString(
      s"""
         |akka.remote.artery.canonical.port = ${port}
         |""".stripMargin)
      .withFallback(ConfigFactory.load("part3_clustering/clusterAwareRouters.conf"))

    val system = ActorSystem("RTJVMCluster", config)
    system.actorOf(Props[SimpleRoutee], "worker")
  }

  startRouteeNode(2551)
  startRouteeNode(2552)
}

object MasterWithRouterAppWithRoundRobinGroup extends App {
  val mainConfig = ConfigFactory.load("part3_clustering/clusterAwareRouters.conf")
  val config = mainConfig.getConfig("masterWithGroupRouterApp").withFallback(mainConfig)

  val system = ActorSystem("RTJVMCluster", config)
  val masterActor = system.actorOf(Props[MasterWithRouter], "master")

  Thread.sleep(10000)
  masterActor ! StartWork
}