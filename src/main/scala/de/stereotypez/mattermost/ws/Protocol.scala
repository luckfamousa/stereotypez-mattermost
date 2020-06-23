package de.stereotypez.mattermost.ws

import spray.json._

object Protocol {

  trait MattermostMessage

  case class EventMessage (
    event: String,
    data: Map[String, String],
    broadcast: Broadcast,
    seq: Long
  )  extends MattermostMessage

  case class StatusMessage(
    status: Option[String],
    seq_reply: Long
  )  extends MattermostMessage

  case class Broadcast(
    omit_users: Option[JsValue],
    user_id: String,
    channel_id: String,
    team_id: String
  )

  case class Post(
    id: String,
    create_at: Long,
    update_at: Long,
    edit_at: Long,
    delete_at: Long,
    is_pinned: Boolean,
    user_id: String,
    channel_id: String,
    root_id: String,
    parent_id: String,
    original_id: String,
    message: String,
    `type`: String,
    props: Map[String, JsValue],
    hashtags: String,
    pending_post_id: String,
    reply_count: Long,
    metadata: Map[String, JsValue]
  )

  // --- spray ---
  object JsonProtocol extends DefaultJsonProtocol
  import JsonProtocol._
  implicit val BroadcastFormat: RootJsonFormat[Broadcast] = jsonFormat4(Broadcast)
  implicit val EventMessageFormat: RootJsonFormat[EventMessage] = jsonFormat4(EventMessage)
  implicit val StatusMessageFormat: RootJsonFormat[StatusMessage] = jsonFormat2(StatusMessage)
  implicit val PostMessageFormat: RootJsonFormat[Post] = jsonFormat18(Post)
}
