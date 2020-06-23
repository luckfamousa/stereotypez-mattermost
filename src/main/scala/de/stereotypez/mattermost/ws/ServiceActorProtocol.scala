package de.stereotypez.mattermost.ws

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Sink

object ServiceActorProtocol {
  sealed trait ServiceActorCommand
  final case class Connect(url: String, authToken: String, sink: Sink[Message, NotUsed]) extends ServiceActorCommand
  final case class Disconnect() extends ServiceActorCommand
}
