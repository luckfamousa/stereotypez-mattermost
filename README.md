# stereotypez-mattermost

(Raw) Scala wrappers and akka-typed behaviors for the Mattermost API.

## Example (using akka-typed)

```scala
  import de.stereotypez.mattermost.ws.bot.BotActor
  import de.stereotypez.mattermost.ws.HookFlow.HookResult
  import de.stereotypez.mattermost.ws.Protocol._
  import de.stereotypez.mattermost.ws.events._
  import akka.actor.typed.ActorSystem

  val hook: BotActor.Hook = {
    case event: WebsocketEvent=>
      case event: WebsocketEvent =>
        println(s"Received ws-event $event")
        event.data match{
          case postData: Posted.PostData=>
            val post = postData.post.convertTo[Posted.Post] //MM returns a json-encoded string
            println(s"Post with message: ${post.message}")
          case statusData: StatusChange.StatusChangeData=>
            println(s"${statusData.user_id} is now ${statusData.status}")
          case _ =>
        }
        HookResult(true, false)
    case resp: WebsocketResponse=>
        println(s"Received ws-response $resp")
        HookResult(true, false)
  }

  val behavior = BotActor.withHooks(
    websocketUrl = config.getString("mattermost.wsurl"),
    botToken = config.getString("mattermost.botToken"),
    hooks = Seq(hook)
  )

  val bot = ActorSystem(behavior, "MyBot")
```