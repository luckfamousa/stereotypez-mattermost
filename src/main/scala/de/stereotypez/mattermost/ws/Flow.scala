package de.stereotypez.mattermost.ws

import akka.http.scaladsl.model.ws.Message
import com.typesafe.scalalogging.LazyLogging
import scala.util.Try
import akka.http.scaladsl.model.ws.TextMessage
import spray.json._
import akka.stream.scaladsl.Sink
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext

object HookFlow extends LazyLogging {
  type Hook = Protocol.MattermostMessage => HookResult
  case class HookResult(handled: Boolean, consumed: Boolean)

  def apply(hooks: Seq[Hook], hookExecutionContext: ExecutionContext) = {
    implicit val ec = hookExecutionContext

    akka.stream.scaladsl.Flow[Message].map {m =>
      Try {
        m match {
          case m: TextMessage.Strict =>
            logger.debug(s"Got message: ${m.text}")
            m.text.parseJson.asJsObject match {
              case ast if ast.fields.contains("event") => ast.convertTo[Protocol.EventMessage]
              case ast if ast.fields.contains("status") => ast.convertTo[Protocol.StatusMessage]
              case _ => throw new MatterMostWsProtocolException("Unknown message type:\n" + m.text.parseJson.prettyPrint)
            }
          case other =>
            throw new MatterMostWsProtocolException(s"Unsupported message format: $other")
        }
      }
    }
    .to(Sink.foreachAsync[Try[Protocol.MattermostMessage]](parallelism = 10) { t =>
      Future(t match {
        case Success(msg) =>
          // run all hooks until the first hook consumes the message
          hooks.exists(_(msg).consumed)
        case Failure(ex) =>
          logger.error("Could not handle Websocket message.", ex)
      })
    })
  }
}