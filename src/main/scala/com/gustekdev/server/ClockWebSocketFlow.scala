package com.gustekdev.server

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.typed.scaladsl.{ ActorSink, ActorSource }

object ClockWebSocketFlow {
  def apply(clockActor: ActorRef[Clock.Command]): Flow[Message, Message, NotUsed] = {
    val incoming: Sink[Message, NotUsed] = Flow[Message]
      .map(Clock.Command.UserRequest)
      .to(
        ActorSink.actorRef[Clock.Command](
          clockActor,
          onCompleteMessage = Clock.Command.Complete,
          onFailureMessage = { case ex => Clock.Command.ConnectionFailure(ex) }
        )
      )
    val outgoing: Source[Message, Unit] = ActorSource
      .actorRef[Clock.Outgoing](
        completionMatcher = { case Clock.Outgoing.Completed => },
        failureMatcher = { case Clock.Outgoing.Failure(ex)  => ex },
        bufferSize = 10,
        OverflowStrategy.dropHead
      )
      .mapMaterializedValue(client => clockActor ! Clock.Command.Connection(client))
      .map {
        case Clock.Outgoing.MessageToClient(msg) => msg
        // These are already handled by completionMatcher and failureMatcher so should never happen
        // added them just to silence exhaustiveness warning
        case Clock.Outgoing.Completed | Clock.Outgoing.Failure(_) => TextMessage.Strict("")
      }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
