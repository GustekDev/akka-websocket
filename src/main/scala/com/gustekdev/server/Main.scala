package com.gustekdev.server

import java.util.concurrent.TimeUnit

import akka.actor.typed.SpawnProtocol.Spawn
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout

object Main extends App {
  val system             = ActorSystem[SpawnProtocol.Command](SpawnProtocol(), "WebSocketServer")
  implicit val timeout   = Timeout(3, TimeUnit.SECONDS)
  implicit val scheduler = system.scheduler
  system.ask[ActorRef[Nothing]](ref => Spawn[Nothing](RootActor(system), "root", Props.empty, ref))
}
