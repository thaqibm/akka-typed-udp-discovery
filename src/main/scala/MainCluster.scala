package ClusterExample
// Akka cluster imports
import akka.actor.{Address, AddressFromURIString}
import akka.actor.typed._
import akka.actor.typed.javadsl.ReceiveBuilder
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter.TypedActorRefOps
import akka.cluster.ClusterEvent._
import akka.cluster.{ClusterEvent, MemberStatus}
import akka.cluster.typed._
import akka.cluster.ClusterEvent.MemberUp
import akka.io.Inet
import akka.util.ByteString
import akka.cluster.metrics.{ClusterMetricsChanged, ClusterMetricsEvent, ClusterMetricsExtension}
import com.typesafe.config.ConfigFactory
import akka.cluster.typed.SingletonActor
import akka.cluster.typed.ClusterSingleton


object Counter{
  sealed trait Command
  case object Increment extends Command
  final case class GetValue(replyTo: ActorRef[Int]) extends Command
  case object GoodByeCounter extends Command

  def apply(): Behavior[Command] = {
    def updated(value: Int): Behavior[Command] = {
      Behaviors.receiveMessage[Command] {
        case Increment =>
          println(s"$Increment: value = ${value + 1}")
          updated(value + 1)
        case GetValue(replyTo) =>
          replyTo ! value
          Behaviors.same
        case GoodByeCounter =>
          // Possible async action then stop
          Behaviors.stopped
      }
    }
    updated(0)
  }
}
object LogActor{
  def apply(): Behavior[MemberEvent] = {
    Behaviors.setup { ctx =>
      println("Created Actor")
      val cluster = Cluster(ctx.system)
      cluster.subscriptions ! Subscribe(ctx.self, classOf[MemberEvent])
      Behaviors.receiveMessage{
        msg =>
          println(s"Leader: ${cluster.state.leader}")
          println(msg)
          Behaviors.same
      }
    }
  }
}
object clusterMetrics {
  def apply(): Behavior[ClusterMetricsEvent] = Behaviors.setup{ ctx =>
    ClusterMetricsExtension(ctx.system).subscribe(ctx.self.toClassic)
    Behaviors.receiveMessage {
      msg => println(msg)
        Behaviors.same
    }
  }
}
object ClusterExample extends App {
  val config = ConfigFactory.load("akka")
  val sys = ActorSystem[MemberEvent](LogActor(), "main", config)
  val cluster = Cluster(sys)
  val seedNodes: List[Address] =
    List("akka://main@127.0.0.1:2554", "akka://main@192.168.224.88:2020").map(AddressFromURIString.parse)


  val singletonManager = ClusterSingleton(sys)
  // Start if needed and provide a proxy to a named singleton
  val proxy: ActorRef[Counter.Command] = singletonManager.init(
    SingletonActor(Behaviors.supervise(Counter()).onFailure[Exception](SupervisorStrategy.restart), "GlobalCounter"))

  println(cluster.selfMember.address)
  cluster.manager ! JoinSeedNodes(seedNodes)
}
