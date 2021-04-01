package com.gustekdev.client

import java.time.ZoneId

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.model.ws.{Message, TextMessage}

import scala.concurrent.duration.{Duration, SECONDS}

object ClockClient {

  sealed trait Command
  object Command {

    case class Subscribe(zoneId: ZoneId) extends Command

    case class ServerResponse(msg: Message) extends Command

    case class Connection(actorRef: ActorRef[ClockClient.Outgoing]) extends Command

    case class ConnectionFailure(ex: Throwable) extends Command

    case object Complete extends Command
  }

  sealed trait Outgoing
  object Outgoing {

    case class MessageToServer(msg: Message) extends Outgoing

    case object Completed extends Outgoing

    case class Failure(ex: Exception) extends Outgoing

  }

  def pending(): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Command.Connection(ref) =>
        Behaviors.withTimers { timers =>
          timers.startSingleTimer(ClockClient.Command.Subscribe(ZoneId.of("UTC")), Duration(5, SECONDS))
          timers.startSingleTimer(ClockClient.Command.Subscribe(ZoneId.of("+2")), Duration(10, SECONDS))
          connected(ref)
        }
      case Command.ConnectionFailure(ex) =>
        ctx.log.warn("WebSocket failed", ex)
        Behaviors.stopped
      case Command.Complete =>
        ctx.log.info("Server closed connection")
        Behaviors.stopped
      case _ =>
        Behaviors.same
    }
  }

  def connected(serverRef: ActorRef[ClockClient.Outgoing]): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Command.Connection(_) => // shouldn't happen at this point
          Behaviors.same
        case Command.Subscribe(tz) =>
          serverRef ! Outgoing.MessageToServer(TextMessage(tz.toString))
          Behaviors.same
        case Command.ServerResponse(TextMessage.Strict(txt)) =>
          ctx.log.info(s"Received $txt")
          Behaviors.same
        case Command.ServerResponse(uk) =>
          ctx.log.warn(s"Received unknown: $uk")
          Behaviors.same
        case Command.ConnectionFailure(ex) =>
          ctx.log.warn("WebSocket failed", ex)
          Behaviors.stopped
        case Command.Complete =>
          ctx.log.info("Server closed connection")
          Behaviors.stopped
      }
    }
}
