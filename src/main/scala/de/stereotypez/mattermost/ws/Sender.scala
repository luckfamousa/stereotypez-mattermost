package de.stereotypez.mattermost.ws

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorSource
import akka.stream.{Materializer, OverflowStrategy}

object Sender {

  import spray.json._

  trait Protocol
  case class ActorMessage(seq: Int, action: String, data: Map[String, String]) extends Protocol
  case object Complete extends Protocol
  case class Fail(ex: Exception) extends Protocol

  object NullOptionsJsonProtocol extends DefaultJsonProtocol with NullOptions
  import NullOptionsJsonProtocol._
  implicit val MessageFormat: RootJsonFormat[ActorMessage] = jsonFormat3(ActorMessage)

  def create(
    bufferSize: Int = 8, 
    overflowStrategy: OverflowStrategy = OverflowStrategy.fail
  )(implicit materializer: Materializer): (ActorRef[Protocol], Source[TextMessage.Strict, NotUsed]) = {
    ActorSource.actorRef[Protocol] (
      completionMatcher = { case Complete => },
      failureMatcher = { case Fail(ex) => ex},
      bufferSize = bufferSize,
      overflowStrategy = overflowStrategy
    )
    .collect { case msg: ActorMessage => msg.toJson.toString() }
    .map(TextMessage(_))
    .preMaterialize()
  }

}
