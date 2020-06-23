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
import de.stereotypez.mattermost.ws.Sender.ActorMessage
import de.stereotypez.mattermost.ws.ServiceActorProtocol.{Connect, Disconnect, ServiceActorCommand}
import de.stereotypez.mattermost.ws.ServiceActorProtocol.ServiceActorCommand

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}


object ServiceActor {

  /* SERVICE SETTINGS */
  case class ServiceSettings(receiveParallelism: Int,
                             outgoingBufferSize: Int,
                             outgoingOverflowStrategy: OverflowStrategy,
                             reconnectTimeout: FiniteDuration) {
    def withReceiveParallelism(receiveParallelism: Int): ServiceSettings =
      ServiceSettings(receiveParallelism, this.outgoingBufferSize, this.outgoingOverflowStrategy, this.reconnectTimeout)
    def withOutgoingBufferSize(outgoingBufferSize: Int): ServiceSettings =
      ServiceSettings(this.receiveParallelism, outgoingBufferSize, this.outgoingOverflowStrategy, this.reconnectTimeout)
    def withOutgoingOverflowStrategy(outgoingOverflowStrategy: OverflowStrategy): ServiceSettings =
      ServiceSettings(this.receiveParallelism, this.outgoingBufferSize, outgoingOverflowStrategy, this.reconnectTimeout)
    def withReconnectTimeout(reconnectTimeout: FiniteDuration): ServiceSettings =
      ServiceSettings(this.receiveParallelism, this.outgoingBufferSize, this.outgoingOverflowStrategy, reconnectTimeout)
  }
  class DefaultServiceSettings extends ServiceSettings(10, 256, OverflowStrategy.fail, 5 seconds)

  def apply(): Behavior[ServiceActorCommand] = {
    ServiceActor(clientSettings = None)
  }

  def apply(serviceSettings: ServiceSettings = new DefaultServiceSettings(),
            clientSettings: Option[ClientConnectionSettings] = None): Behavior[ServiceActorCommand] =
    Behaviors
      .supervise[ServiceActorCommand](
        Behaviors.setup(serviceActorImpl(_, serviceSettings, clientSettings))
      )
      .onFailure(SupervisorStrategy.restart)


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

                context.log.info(s"Connecting to Websocket service '$url' ...")

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
                      context.log.info(s"Connected to Websocket service '$url'")
                      ref ! ActorMessage(1, "authentication_challenge", Map("token" -> authToken))
                    case Success(upgrade) =>
                      context.log.warn(s"Connection to Websocket service '$url' failed: ${upgrade.response.status}")
                    case Failure(ex) =>
                      context.log.error(s"Connection to Websocket service '$url' failed", ex)
                  })
              }
            }}
            .viaMat(KillSwitches.single)(Keep.right)
            .toMat(sink)(Keep.both)
            .run()

          killableBehavior(killswitch)

        case Disconnect() =>
          context.log.warn("Not connected to websocket.")
          Behaviors.same
      }
    }

    def killableBehavior(killswitch: UniqueKillSwitch): Behaviors.Receive[ServiceActorCommand] = {
      Behaviors.receiveMessage[ServiceActorCommand] {
        case Disconnect() =>
          killswitch.shutdown()
          connectableBehavior()
        case Connect(_, _, _) =>
          context.log.warn("Cannot connect, already connected to websocket.")
          Behaviors.same
      }
    }

    connectableBehavior()
  }

}