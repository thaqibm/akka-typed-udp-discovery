
package server

import akka.actor
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

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

final case class InetProtocolFamily() extends DatagramChannelCreator {
  override def create(): DatagramChannel =
    DatagramChannel.open(StandardProtocolFamily.INET)
}
final case class MulticastGroup(group: InetAddress) extends AbstractSocketOptionV2 {
  override def afterBind(s: DatagramSocket): Unit = {
    try {
      val networkInterface = NetworkInterface.getNetworkInterfaces.asScala.find{
        x => (x != null) && (x.getName.startsWith("w") || x.getName.startsWith("e"))
      }.get
      s.getChannel.join(group, networkInterface)
    }
    catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}

sealed trait UdpMessage
final case class UdpEvent(event: Udp.Event, sender: ActorRef[UdpMessage]) extends UdpMessage
final case class UdpCmd(event: Udp.Command, sender: ActorRef[UdpMessage]) extends UdpMessage
final case class Send(sender: ActorRef[UdpMessage]) extends UdpMessage
final case class Unbound(sender: ActorRef[UdpMessage]) extends UdpMessage

object AppActor{
  def apply(localInet: InetSocketAddress, udpMulticastAddr: InetAddress) = Behaviors.setup[UdpMessage]{
    ctx =>
      implicit val sys: actor.ActorSystem = ctx.system.toClassic
      val opts = List(InetProtocolFamily(), MulticastGroup(udpMulticastAddr))
      IO(Udp) ! Udp.Bind(ctx.self.toClassic, localInet, opts)
      Behaviors.receiveMessagePartial {
        case UdpEvent(Udp.Bound(localAddress), udpConnection) => {
          println(s"Bound from ${localAddress}, ref: ${udpConnection}")
          Behaviors.receiveMessagePartial{
            case UdpEvent(Udp.Received(data, senderIp), _) => {
              println(s"Received ${data.utf8String} from ${senderIp}")
              val send = Udp.Send(ByteString("ACK"), senderIp)
              udpConnection ! UdpCmd(send, ctx.self)
              Behaviors.same
            }
            case UdpCmd(Udp.Unbind, _) => {
              udpConnection ! UdpCmd(Udp.Unbind, ctx.self)
              Behaviors.same
            }
            case Unbound(_) => {
              Behaviors.stopped
            }
          }
        }
      }
  }
}
object MainUDP extends App {
  val config = ConfigFactory.load("akka")
  val port = config.getInt("udp-multicast-port")
  val multicastStr = config.getString("udp-multicast-address")
  val localInet = new InetSocketAddress(port)
  val multicastAddr = InetAddress.getByName(multicastStr)

  val sys = ActorSystem(AppActor(localInet, multicastAddr), "ScalaAkkaUDPDiscovery")
}
