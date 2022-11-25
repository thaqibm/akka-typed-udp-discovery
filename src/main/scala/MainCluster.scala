package ClusterExample
// Akka cluster imports
import akka.actor.{Address, AddressFromURIString}
import akka.actor.typed._
import akka.actor.typed.javadsl.ReceiveBuilder
import akka.actor.typed.scaladsl._
import akka.cluster.ClusterEvent._
import akka.cluster.{ClusterEvent, MemberStatus}
import akka.cluster.typed._
import akka.cluster.ClusterEvent.MemberUp
import akka.io.Inet
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

// UDP Multicast imports:

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
object ClusterExample extends App {
  val config = ConfigFactory.load("akka")
  val sys = ActorSystem[MemberEvent](LogActor(), "main", config)
  val cluster = Cluster(sys)
  val seedNodes: List[Address] =
  List("akka://main@192.168.224.141:2554", "akka://main@192.168.224.88:2020").map(AddressFromURIString.parse)

  println(cluster.selfMember.address)
  cluster.manager ! JoinSeedNodes(seedNodes)
}
