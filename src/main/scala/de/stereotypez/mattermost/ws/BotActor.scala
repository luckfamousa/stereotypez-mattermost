package de.stereotypez.mattermost.ws

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.model.ws.WebSocketUpgradeResponse
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import de.stereotypez.mattermost.ws.Sender.ActorMessage

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import akka.stream.KillSwitches
import akka.actor.typed.PostStop
import scala.concurrent.ExecutionContext

object BotActor extends LazyLogging {
  
  sealed trait Command
  private case object Connected extends Command
  private case object ConnectionClosed extends Command
  private case class ConnectionError(t: Throwable) extends Command

  case class UpgradeFailedException(websocketUrl: String)
    extends RuntimeException(s"Websocket upgrade failed at $websocketUrl")

  /* SERVICE SETTINGS */
  case class ServiceSettings(
    receiveParallelism: Int = 10,
    outgoingBufferSize: Int = 256,
    outgoingOverflowStrategy: OverflowStrategy = OverflowStrategy.fail,
    reconnectTimeout: FiniteDuration = 5.seconds,
    hookExecutionContext: ExecutionContext = ExecutionContext.global
  )

  def withHooks(
    websocketUrl: String, 
    botToken: String, 
    hooks: Seq[HookFlow.Hook],
    serviceSettings: ServiceSettings = ServiceSettings(),
    clientSettings: Option[ClientConnectionSettings] = None
  ): Behavior[Command] = {
    withSink(
      websocketUrl,
      botToken,
      HookFlow(hooks, serviceSettings.hookExecutionContext),
      serviceSettings,
      clientSettings
    )
  }

  def withSink(
    websocketUrl: String, 
    botToken: String, 
    sink: Sink[Message, NotUsed],
    serviceSettings: ServiceSettings = ServiceSettings(),
    clientSettings: Option[ClientConnectionSettings] = None
  ): Behavior[Command] = Behaviors.supervise[Command](
    serviceActorImpl(websocketUrl, botToken, sink, serviceSettings, clientSettings)
  ).onFailure(SupervisorStrategy.restartWithBackoff(1.second, 5.seconds, 0.2))


  private def serviceActorImpl(
    websocketUrl: String,
    botToken: String,
    sink: Sink[Message, NotUsed],
    serviceSettings: ServiceSettings,
    clientSettings: Option[ClientConnectionSettings]
  ): Behavior[Command] = Behaviors.setup { context =>
    implicit val classicSystem: ActorSystem = context.system.toClassic

    logger.info(s"Connecting to Websocket service '$websocketUrl' ...")

    val wsSettings = clientSettings.getOrElse(ClientConnectionSettings(classicSystem).withWebsocketSettings(
      ClientConnectionSettings(classicSystem)
        .websocketSettings.withPeriodicKeepAliveMaxIdle(1 minutes)
      )
    )
        
    val (sender: ActorRef[Sender.Protocol], wsOutgoing: Source[TextMessage.Strict, NotUsed]) = Sender.create(
      serviceSettings.outgoingBufferSize,
      serviceSettings.outgoingOverflowStrategy
    )
      
    val webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(
      request = WebSocketRequest(websocketUrl),
      settings = wsSettings
    )

    val wsIncoming = sink
    
    val ((upgradeResponse, killSwitch), closed) = wsOutgoing
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .viaMat(KillSwitches.single)(Keep.both)
      .toMat(wsIncoming)(Keep.both) // also keep the Future[Done]
      .run()

    context.pipeToSelf(upgradeResponse) { 
      case Success(upgrade) if upgrade.response.status == StatusCodes.SwitchingProtocols =>
        Connected
      case Success(upgrade) =>
        ConnectionError(UpgradeFailedException(websocketUrl))
      case Failure(ex) =>
        ConnectionError(ex)
    }

    Behaviors.receiveMessage[Command] {
      case Connected =>
        context.log.info(s"Connected to $websocketUrl, authentiating...")
        sender ! ActorMessage(1, "authentication_challenge", Map("token" -> botToken))
        Behaviors.same
      case ConnectionClosed => 
        context.log.error(s"Websocket connection closed. Shutting down...")
        Behaviors.stopped
      case ConnectionError(t) => 
        context.log.error(s"Error connecting to WS service, shutting down...", t)
        Behaviors.stopped
    }.receiveSignal {
      case (_, PostStop) =>
        context.log.warn("Received PostStop signal, stopping websockets...")
        killSwitch.shutdown()
        Behaviors.stopped
    }
  }
}