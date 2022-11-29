
package server

import ClusterExample.LogActor
import akka.actor.AddressFromURIString
import akka.{actor => classic}

import java.net.{InetAddress, InetSocketAddress, NetworkInterface, StandardProtocolFamily}
import java.net.DatagramSocket
import java.nio.channels.DatagramChannel
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.io.Inet.{AbstractSocketOptionV2, DatagramChannelCreator, SocketOptionV2}
import akka.io.{IO, Udp}
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.typed.{Cluster, Join, JoinSeedNodes}
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

object loggerSink{
  def apply() = Behaviors.receiveMessage[String]{
    msg => println(s"loggerSink: ${msg}")
      Behaviors.same
  }
}

class ServerActor(val localInetAddr: InetSocketAddress, val udpMulticastAddr: InetAddress, sink: ActorRef[String]) extends classic.Actor {

  import context._
  override def preStart(): Unit = {
    super.preStart()
    val udpManager = IO(Udp)
    val udpOpts = List(InetProtocolFamily(), MulticastGroup(udpMulticastAddr))
    udpManager ! Udp.Bind(self, localInetAddr, udpOpts)
  }

  override def receive: Receive = {
    case Udp.Bound(localAddress) => {
      println(s"UDP Bound to ${localAddress}")
      val udpConn = sender()
      context.become(ready(udpConn))
    }
  }
  def ready(udpConnection: classic.ActorRef): Receive  ={
    case Udp.Received(data, senderIp) =>{
      sink ! data.utf8String
      val cluster = Cluster(context.system.toTyped)
      println(cluster.manager.path)
      udpConnection ! Udp.Send(ByteString(cluster.selfMember.address.toString), senderIp)
    }
    case b@Udp.Unbind => {
      udpConnection ! b
    }
    case Udp.Unbound => {
      stop(self)
    }
  }
}
object MainUDP extends App {
  val config = ConfigFactory.load("akka")
  val port = config.getInt("udp-multicast-port")
  val multicastStr = config.getString("udp-multicast-address")
  val localInet = new InetSocketAddress(port)
  val multicastAddr = InetAddress.getByName(multicastStr)

  val sys = classic.ActorSystem("sys", config)
  val sinkActor = sys.spawn(loggerSink(), "logger")

  val cluster = Cluster(sys.toTyped)

  cluster.manager ! JoinSeedNodes(Seq(cluster.selfMember.address))
  println(cluster.manager.path)
  sys.spawn(LogActor(), "cluster-logger")

  sys.actorOf(classic.Props(classOf[ServerActor], localInet, multicastAddr, sinkActor), "serverActor")
}
