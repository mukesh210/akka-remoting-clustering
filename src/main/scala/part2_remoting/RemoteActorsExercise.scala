package part2_remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSystem, Identify, PoisonPill, Props}
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ListBuffer

object WordCountDomain {
  case class Initialize(nWorkers: Int)
  case class WordCountTask(text: String)
  case class WordCountResult(count: Int)
  case object EndWordCount
}

class WordCountWorker extends Actor with ActorLogging {
  import WordCountDomain._

  override def receive: Receive = {
    case WordCountTask(text) =>
      log.info(s"I am processing: $text")
      sender() ! WordCountResult(text.split(" ").length)
  }
}

class WordCountMaster extends Actor with ActorLogging {
  import WordCountDomain._
  var workerActorRefs = ListBuffer.empty[ActorRef]

  val workerRouter = context.actorOf(FromConfig.props(Props[WordCountWorker]), "workerRouter")

  override def receive: Receive = onlineWithRouters(0, 0)

  def onlineWithRouters(remainingTasks: Int, totalCount: Int): Receive = {
    case text: String =>
      // split it into sentences
      val sentences = text.split("\\. ")
      // send sentences to each of the workers in turn
      sentences.foreach(sentence => workerRouter ! WordCountTask(sentence))
      context.become(onlineWithRouters(remainingTasks + sentences.length, totalCount))

    case WordCountResult(count) =>
      if(remainingTasks == 1) {
        log.info(s"TOTAL RESULT: ${totalCount + count}")
        context.stop(self)
      } else {
        context.become(onlineWithRouters(remainingTasks - 1, totalCount + count))
      }
  }

  def waitingToInitializeWorkers(nWorkers: Int): Receive = {
    case Initialize(nWorkers) =>
      val actorRefs = (1 to nWorkers).map(i => context.actorOf(Props[WordCountWorker], s"wordCountWorker_${i}")).toList
      context.become(online(actorRefs, 0, 0))
  }

  def identifyWorkers(nWorkers: Int) = {
    val workerSelections = (1 to nWorkers).map(i => context.actorSelection(s"akka://WorkersSystem@localhost:2552/remote/akka/MasterSystem@localhost:2551/user/wordCountMaster/wordCountWorker_${i}"))
    workerSelections.foreach(_ ! Identify(25))
    context.become(initializing(List(), nWorkers))
  }

  def initializing(workerActorRefs: List[ActorRef], remainingWorkers: Int): Receive = {
    case ActorIdentity(25, Some(actorRef: ActorRef)) =>
      if(remainingWorkers == 1)
        context.become(online(actorRef :: workerActorRefs, 0, 0))
      else
        context.become(initializing(actorRef :: workerActorRefs, remainingWorkers - 1))
  }

  def online(workers: List[ActorRef], remainingTasks: Int, totalCount: Int): Receive = {
    case text: String =>
      // split it into sentences
      val sentences = text.split("\\. ")
      // send sentences to each of the workers in turn
      Iterator.continually(workers).flatten.zip(sentences.iterator).foreach{ pair =>
        val (worker, sentence) = pair
        worker ! WordCountTask(sentence)
      }
      context.become(online(workers, remainingTasks + sentences.length, totalCount))

    case WordCountResult(count) =>
      if(remainingTasks == 1) {
        log.info(s"TOTAL RESULT: ${totalCount + count}")
        workers.foreach(_ ! PoisonPill)
        context.stop(self)
      } else {
        context.become(online(workers, remainingTasks - 1, totalCount + count))
      }
  }
}

object MasterApp extends App {
  import WordCountDomain._
  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2551
      |""".stripMargin
  ).withFallback(ConfigFactory.load("part2_remoting/remoteActorsExercise.conf"))

  val system = ActorSystem("MasterSystem", config)
  val master = system.actorOf(Props[WordCountMaster], "wordCountMaster")
  master ! Initialize(5)
  Thread.sleep(1000)

  scala.io.Source.fromFile("src/main/resources/txt/lipsum.txt").getLines().foreach { line =>
    master ! line
  }
}

object WorkersApp extends App {
  import WordCountDomain._
  val config = ConfigFactory.parseString(
    """
      |akka.remote.artery.canonical.port = 2552
      |""".stripMargin
  ).withFallback(ConfigFactory.load("part2_remoting/remoteActorsExercise.conf"))

  val system = ActorSystem("WorkersSystem", config)
  // (1 to 5).map(i => system.actorOf(Props[WordCountWorker], s"wordCountWorker_${i}"))
}
