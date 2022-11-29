package PeerSystem

import akka.{actor => classic}
import com.typesafe.config.ConfigFactory

object Main extends App {
  /* TODO
   * [] Cluster
   * [] UDP Singleton
   * [] Test on multiple machines
   */
  val config = ConfigFactory.load("akka")
  val name = config.getString("system-name")
  val system = classic.ActorSystem(name, config)
  println(s"started sys:${system.name}")
}
