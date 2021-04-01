package com.gustekdev.client

import java.time.ZoneId

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.{Done, NotUsed}

import scala.concurrent.Future
import scala.io.StdIn

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem[NotUsed](
      Behaviors.setup { ctx =>
        implicit val actorSystem      = ctx.system
        implicit val executionContext = actorSystem.executionContext
        val clock                     = ctx.spawnAnonymous(ClockClient.pending())

        Http().singleWebSocketRequest(
          WebSocketRequest("ws://127.0.0.1:8080/clock"), ClockClientFlow(clock)
        )

        Behaviors.empty
      },
      "WebSocketClient"
    )
  }
}
