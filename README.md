# stereotypez-mattermost

(Raw) Scala wrappers and akka-typed behaviors for the Mattermost API.

## Example (using akka-typed)

```scala
  import de.stereotypez.mattermost.ws.bot.BotActor
  import de.stereotypez.mattermost.ws.HookFlow.HookResult
  import de.stereotypez.mattermost.ws.Protocol._
  import akka.actor.typed.ActorSystem

  val hook: BotActor.Hook = {
    case event: EventMessage if event.event == "posted" =>
      // do sth. with event message
      println(event)
      HookResult(handled = true, consumed = false)
    case _ =>
      HookResult(handled = false, consumed = false)
  }

  val behavior = BotActor.withHooks(
    websocketUrl = config.getString("mattermost.wsurl"),
    botToken = config.getString("mattermost.botToken"),
    hooks = Seq(hook)
  )

  val bot = ActorSystem(behavior, "MyBot")
```