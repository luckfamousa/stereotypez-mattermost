package de.stereotypez.mattermost.ws

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.scaladsl.{Flow, Keep, RestartSource, Source}
import akka.stream.{KillSwitches, OverflowStrategy, UniqueKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import de.stereotypez.mattermost.ws.Sender.ActorMessage
import de.stereotypez.mattermost.ws.ServiceActorProtocol.{Connect, Disconnect, ServiceActorCommand}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ServiceActor extends LazyLogging {

  /* SERVICE SETTINGS */
  case class ServiceSettings(receiveParallelism: Int,
                             outgoingBufferSize: Int,
                             outgoingOverflowStrategy: OverflowStrategy,
                             reconnectTimeout: FiniteDuration) {
    def withReceiveParallelism(receiveParallelism: Int): ServiceSettings                          = this.copy(receiveParallelism = receiveParallelism)
    def withOutgoingBufferSize(outgoingBufferSize: Int): ServiceSettings                          = this.copy(outgoingBufferSize = outgoingBufferSize)
    def withOutgoingOverflowStrategy(outgoingOverflowStrategy: OverflowStrategy): ServiceSettings = this.copy(outgoingOverflowStrategy = outgoingOverflowStrategy)
    def withReconnectTimeout(reconnectTimeout: FiniteDuration): ServiceSettings                   = this.copy(reconnectTimeout = reconnectTimeout)
  }

  val DefaultServiceSettings = ServiceSettings(10, 256, OverflowStrategy.fail, 5 seconds)

  def apply(): Behavior[ServiceActorCommand] = {
    ServiceActor(clientSettings = None)
  }

  def apply(
    serviceSettings: ServiceSettings = DefaultServiceSettings,
    clientSettings: Option[ClientConnectionSettings] = None
  ): Behavior[ServiceActorCommand] = Behaviors.supervise[ServiceActorCommand](
    Behaviors.setup(serviceActorImpl(_, serviceSettings, clientSettings))
  ).onFailure(SupervisorStrategy.restartWithBackoff(1.second, 5.seconds, 0.2))


  private def serviceActorImpl(context: ActorContext[ServiceActorCommand],
                               serviceSettings: ServiceSettings,
                               clientSettings: Option[ClientConnectionSettings]): Behaviors.Receive[ServiceActorCommand] = {

    def connectableBehavior(): Behaviors.Receive[ServiceActorCommand] = {
      Behaviors.receiveMessage[ServiceActorCommand] {

        case Connect(url, authToken, sink) =>

          implicit val system: ActorSystem = context.system.toClassic
          implicit val ec: ExecutionContextExecutor = context.executionContext

          val (killswitch, done) =
            RestartSource.withBackoff(1.second, 30.seconds, randomFactor = 0.2) { () => Source.futureSource {
              Future {

                logger.info(s"Connecting to Websocket service '$url' ...")
                
                val webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(
                  request = WebSocketRequest(url),
                  settings = clientSettings.getOrElse(ClientConnectionSettings(system)
                    .withWebsocketSettings(
                      ClientConnectionSettings(system)
                        .websocketSettings.withPeriodicKeepAliveMaxIdle(1 minutes))))

                val (ref: ActorRef[Sender.Protocol], outgoing: Source[TextMessage.Strict, NotUsed]) = Sender.create(
                  serviceSettings.outgoingBufferSize,
                  serviceSettings.outgoingOverflowStrategy
                )

                outgoing
                  .viaMat(webSocketFlow)(Keep.right)
                  .mapMaterializedValue(_.onComplete {
                    case Success(upgrade) if upgrade.response.status == StatusCodes.SwitchingProtocols =>
                      logger.info(s"Connected to Websocket service '$url'")
                      ref ! ActorMessage(1, "authentication_challenge", Map("token" -> authToken))
                    case Success(upgrade) =>
                      logger.warn(s"Connection to Websocket service '$url' failed: ${upgrade.response.status}")
                    case Failure(ex) =>
                      logger.error(s"Connection to Websocket service '$url' failed", ex)
                  })
              }
            }}
            .viaMat(KillSwitches.single)(Keep.right)
            .toMat(sink)(Keep.both)
            .run()

          killableBehavior(killswitch)

        case Disconnect() =>
          logger.warn("Not connected to websocket.")
          Behaviors.same
      }
    }

    def killableBehavior(killswitch: UniqueKillSwitch): Behaviors.Receive[ServiceActorCommand] = {
      Behaviors.receiveMessage[ServiceActorCommand] {
        case Disconnect() =>
          killswitch.shutdown()
          connectableBehavior()
        case Connect(_, _, _) =>
          logger.warn("Cannot connect, already connected to websocket.")
          Behaviors.same
      }
    }

    connectableBehavior()
  }

}