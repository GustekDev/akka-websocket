package com.gustekdev.client

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}

object ClockClientFlow {
  def apply(clockClientActor: ActorRef[ClockClient.Command]) = {
    val sink: Sink[Message, NotUsed] = Flow[Message]
      .map(ClockClient.Command.ServerResponse)
      .to(
        ActorSink.actorRef[ClockClient.Command](
          clockClientActor,
          onCompleteMessage = ClockClient.Command.Complete,
          onFailureMessage = { case ex => ClockClient.Command.ConnectionFailure(ex) }
        )
      )

    val outgoing: Source[Message, Unit] = ActorSource
      .actorRef[ClockClient.Outgoing](
        completionMatcher = { case ClockClient.Outgoing.Completed => },
        failureMatcher = { case ClockClient.Outgoing.Failure(ex)  => ex },
        bufferSize = 10,
        OverflowStrategy.dropHead
      )
      .mapMaterializedValue(client => clockClientActor ! ClockClient.Command.Connection(client))
      .map {
        case ClockClient.Outgoing.MessageToServer(msg) => msg
        // These are already handled by completionMatcher and failureMatcher so should never happen
        // added them just to silence exhaustiveness warning
        case ClockClient.Outgoing.Completed | ClockClient.Outgoing.Failure(_) => TextMessage.Strict("")
      }

    Flow.fromSinkAndSource(sink, outgoing)
  }
}
