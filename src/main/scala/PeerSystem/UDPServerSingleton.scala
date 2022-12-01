package PeerSystem

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.{TypedActorContextOps, TypedActorRefOps}
import akka.io.Inet.{AbstractSocketOptionV2, DatagramChannelCreator}

import java.net.{DatagramSocket, InetAddress, InetSocketAddress, NetworkInterface, StandardProtocolFamily}
import java.nio.channels.DatagramChannel
import scala.jdk.CollectionConverters._


final case class InetProtocolFamily() extends DatagramChannelCreator {
  override def create(): DatagramChannel =
    DatagramChannel.open(StandardProtocolFamily.INET)
}
final case class MulticastGroup(group: InetAddress) extends AbstractSocketOptionV2 {
  override def afterBind(s: DatagramSocket): Unit = {
    try {
      val networkInterface = NetworkInterface.getNetworkInterfaces.asScala.find{
        x => (x != null) && x.supportsMulticast && x.isUp &&
          x.getInetAddresses.asScala.exists(_.isInstanceOf[InetAddress])
      }.get
      s.getChannel.join(group, networkInterface)
    }
    catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}

object UDPServerSingleton {
  def apply(localInetAddr: InetSocketAddress, udpMulticastAddr: InetAddress) = Behaviors.setup[String] { ctx =>
      // spawn classic: ctx.actorOf()
      ???
  }
}