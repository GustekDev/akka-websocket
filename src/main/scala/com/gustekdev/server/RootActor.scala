package com.gustekdev.server

import java.util.concurrent.TimeUnit

import akka.actor.typed.SpawnProtocol.Spawn
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, Props, SpawnProtocol }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{ handleWebSocketMessages, path }
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

object RootActor {
  def apply(system: ActorSystem[SpawnProtocol.Command]): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
    implicit val timeout   = Timeout(3, TimeUnit.SECONDS)
    implicit val scheduler = system.scheduler

    val websocketRoute =
      path("clock") {
        val clientF = system.ask[ActorRef[Clock.Command]] { ref =>
          Spawn[Clock.Command](
            Clock.pending(),
            "client",
            props = Props.empty,
            replyTo = ref
          )
        }
        val client = Await.result(clientF, Duration.Inf)
        handleWebSocketMessages(
          ClockWebSocketFlow(client)
        )
      }

    startHttpServer(websocketRoute)(system)

    Behaviors.empty
  }

  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
}
