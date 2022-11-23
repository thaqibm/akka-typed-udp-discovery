import java.net.InetSocketAddress
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
import server.{Send, UdpCmd, UdpEvent, UdpMessage, Unbound}

import scala.concurrent.duration.DurationInt
object ClientActor{
  val TIMER_KEY = "TIMER_KEY"
  def apply(localInet: InetSocketAddress, remoteInet: InetSocketAddress) = Behaviors.setup[UdpMessage]{
    ctx => {
      implicit val sys: actor.ActorSystem = ctx.system.toClassic
      IO(Udp) ! Udp.Bind(ctx.self.toClassic, localInet)
      Behaviors.withTimers{
        timer => {
          timer.startTimerWithFixedDelay(TIMER_KEY, Send(ctx.self),1.seconds)
          Behaviors.receiveMessagePartial {
            case UdpEvent(Udp.Received(data, senderIp), _) => {
              println(s"Received message ${data.utf8String} from: ${senderIp}")
              timer.cancel(TIMER_KEY)
              Behaviors.same
            }
            case Send(sender) => {
              println("Sending discovery message to server")
              sender ! UdpCmd(Udp.Send(ByteString("CLIENT_DISCOVERY"), remoteInet), ctx.self)
              Behaviors.same
            }
            case Unbound(sender) =>{
              Behaviors.stopped
            }
          }
        }
      }
    }
  }
}
object UDPClient extends App {
  val config = ConfigFactory.load("akka")
  val remoteInet = new InetSocketAddress(
    InetAddress.getByName(config.getString("udp-multicast-address")),
    config.getInt("udp-multicast-port")
  )
  val localInet = new InetSocketAddress(0)
  val sys = ActorSystem(ClientActor(localInet, remoteInet), "ScalaAkkaUDPDiscoverCLient")
}
