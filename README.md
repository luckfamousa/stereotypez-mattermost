# stereotypez-mattermost
Scala wrappers around mattermost4j and akka based websockets

### Example
```scala
  val myBot: Bot = Bot(
    config.getString("mattermost.url"),
    config.getString("mattermost.wsurl"),
    config.getString("mattermost.bots.myBot.token"),
    system /* ActorSystem */)
    .withHook {
      case event: EventMessage if event.event == "posted" =>
        // do sth. with event message
        handleEventMessage(event)
        HookResult(handled = true, consumed = false)
      case _ =>
        HookResult(handled = false, consumed = false)
    }
    .connect()
```