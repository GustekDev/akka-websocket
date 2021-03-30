package com.gustekdev.server

import java.time.{ ZoneId, ZonedDateTime }
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.http.scaladsl.model.ws.{ Message, TextMessage }

import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success, Try }

object Clock {

  sealed trait Command
  object Command {

    case class UserRequest(msg: Message) extends Command

    case class Connection(actorRef: ActorRef[Clock.Outgoing]) extends Command

    case class ConnectionFailure(ex: Throwable) extends Command

    case object Complete extends Command

    case object Tick extends Command

  }

  sealed trait Outgoing
  object Outgoing {

    case class MessageToClient(msg: Message) extends Outgoing

    case object Completed extends Outgoing

    case class Failure(ex: Exception) extends Outgoing

  }

  def pending(): Behavior[Command] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Command.Connection(ref) =>
        Behaviors.withTimers { timers =>
          timers.startTimerAtFixedRate(Command.Tick, Duration.apply(1, TimeUnit.SECONDS))
          connected(ref, Seq.empty)
        }
      case Command.ConnectionFailure(ex) =>
        ctx.log.warn("WebSocket failed", ex)
        Behaviors.stopped
      case Command.Complete =>
        ctx.log.info("User closed connection")
        Behaviors.stopped
      case _ => Behaviors.same
    }
  }

  def connected(actorRef: ActorRef[Clock.Outgoing], timeZones: Seq[ZoneId]): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Command.Connection(_) => // shouldn't happen at this point
          Behaviors.same
        case Command.UserRequest(TextMessage.Strict(txt)) =>
          Try {
            ZoneId.of(txt)
          } match {
            case Success(tz) =>
              actorRef ! Outgoing.MessageToClient(TextMessage(s"Subscribed to $tz"))
              connected(actorRef, timeZones :+ tz)
            case Failure(ex) =>
              actorRef ! Outgoing.MessageToClient(TextMessage(ex.getMessage))
              Behaviors.same
          }

        case Command.UserRequest(uk) =>
          actorRef ! Outgoing.MessageToClient(TextMessage(s"Received unknown: $uk"))
          Behaviors.same
        case Command.Tick =>
          actorRef ! Outgoing.MessageToClient(
            TextMessage(timeZones.map(tz => tz.toString -> ZonedDateTime.now(tz)).toString())
          )
          Behaviors.same
        case Command.ConnectionFailure(ex) =>
          ctx.log.warn("WebSocket failed", ex)
          Behaviors.stopped
        case Command.Complete =>
          ctx.log.info("User closed connection")
          Behaviors.stopped
      }
    }
}
