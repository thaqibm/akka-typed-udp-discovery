import java.net.InetSocketAddress
import akka.{actor => classic}

import java.net.{InetAddress, InetSocketAddress, NetworkInterface, StandardProtocolFamily}
import java.net.DatagramSocket
import java.nio.channels.DatagramChannel
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.io.Inet.{AbstractSocketOptionV2, DatagramChannelCreator, SocketOptionV2}
import akka.io.{IO, Udp}
import akka.actor.typed.scaladsl.adapter._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import akka.cluster.typed._
import scala.concurrent.duration.DurationInt



object loggerActor{
  def apply(): Behavior[String] = Behaviors.receiveMessage{
    msg => println(s"loggerActor: ${msg}")
      Behaviors.same
  }
}
class UdpListenerActor(localInet: InetSocketAddress, remoteInet: InetSocketAddress, sink: ActorRef[String]) extends classic.Actor with classic.Timers {
  override def preStart(): Unit = {
    super.preStart()
    val manager = Udp(context.system).manager
    println(s"Sending req to bind to ${localInet}")
    manager ! Udp.Bind(self, localInet)
    println(self.path.address.host)
  }

  override def receive: Receive = {
    case Udp.Bound(_) => {
      timers.startTimerWithFixedDelay("DISCOVERY", SendDiscovery, 1.seconds)
      context.become(ready(sender())) // spawn new actor here with the sender ref
    }
  }
  private def ready(udpRef: classic.ActorRef): Receive = {
    case Udp.Received(data, senderIp) => {
      sink ! data.utf8String
      timers.cancel("DISCOVERY")
    }
    case SendDiscovery => {
      println("Sending Discovery to server")
      udpRef ! Udp.Send(ByteString("Hi"), remoteInet)
    }
    case b@Udp.Unbind => {
      udpRef ! b
    }
    case Udp.Unbound => {
      context.stop(self)
    }
  }
  object SendDiscovery
}

object UDPClient extends App {
  val config = ConfigFactory.load("akka")
  val remoteInet = new InetSocketAddress(
    InetAddress.getByName(config.getString("udp-multicast-address")),
    config.getInt("udp-multicast-port")
  )
  val localInet = new InetSocketAddress(0)
  val sys = classic.ActorSystem("sys", config)
  val sink = sys.spawn(loggerActor(), "msgLogger")
  sys.actorOf(classic.Props(classOf[UdpListenerActor], localInet, remoteInet, sink), "listener")
}
