package PeerSystem
import akka.actor.AddressFromURIString
import akka.{actor => classic}
import akka.actor.typed._
import akka.cluster.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorRefOps
import akka.event.{Logging, LoggingAdapter}
import akka.io.{IO, Udp}
import akka.util.ByteString

import java.net.InetSocketAddress
import scala.concurrent.duration.DurationInt

sealed trait ClientRequest
case class JoinCluster(address: String, replyTo: Option[ActorRef[Any]]) extends ClientRequest
object StartDiscovery

class UDPClientActor(localInet: InetSocketAddress,
                     remoteInet: InetSocketAddress,
                     sink: ActorRef[ClientRequest])
  extends classic.Actor with classic.Timers {

  import context._
  val log = Logging(context.system, this)
  private val DISCOVERY_TIMER_KEY = "DISCOVERY"
  private val delay = 1.seconds
  override def preStart(): Unit = {
    super.preStart()
    val udpManager = IO(Udp)
    udpManager ! Udp.Bind(self, localInet)
  }

  override def receive: Receive = {
    case Udp.Bound(_) => {
      // Bound to udp and ready to send requests
      timers.startTimerWithFixedDelay(DISCOVERY_TIMER_KEY, SendDiscovery, delay)
      context.become(ready(sender()))
    }
  }
  private def ready(UdpRef: classic.ActorRef): Receive = {
    case Udp.Received(data, _) => {
      // Received data back
      sink ! JoinCluster(data.utf8String, Some(self.toTyped))
      timers.cancel()
    }
    case StartDiscovery => {
      timers.startTimerWithFixedDelay(DISCOVERY_TIMER_KEY, SendDiscovery, delay)
    }
    case SendDiscovery => {
      log.info(s"Sending CLIENT_DISCOVERY to ${remoteInet}")
      UdpRef ! Udp.Send(ByteString("CLIENT_DISCOVERY"), remoteInet)
    }
    case b@Udp.Unbind => UdpRef ! b
    case Udp.Unbound => {
      context.stop(self)
    }
  }
  object SendDiscovery
}

object ClientSink{
  def apply(): Behavior[ClientRequest] = Behaviors.setup{ctx =>
    val cluster = Cluster(ctx.system)
    Behaviors.receiveMessage {
      case JoinCluster(address, _) => {
        val addr = AddressFromURIString.parse(address)
        cluster.manager ! Join(addr)
        Behaviors.same
      }
    }
  }
}
