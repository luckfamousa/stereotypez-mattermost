package de.stereotypez.mattermost.ws

import spray.json._

object Protocol {

  trait MattermostMessage

  case class EventMessage (
    event: String,
    data: Option[Data],
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

  case class Data(
    channel_display_name: Option[String],
    channel_name: Option[String],
    channel_type: Option[String],
    mentions: Option[String],
    post: Option[String],
    sender_name: Option[String],
    set_online: Option[Boolean],
    team_id: Option[String],
    status: Option[String],
    user_id: Option[String]
  )

  case class Post(
    id: String,
    create_at: Long,
    update_at: Option[Long],
    edit_at: Option[Long],
    delete_at: Option[Long],
    is_pinned: Boolean,
    user_id: String,
    channel_id: String,
    root_id: Option[String],
    parent_id: Option[String],
    original_id: Option[String],
    message: String,
    `type`: Option[String],
    props: Map[String, JsValue],
    hashtags: Option[String],
    pending_post_id: Option[String],
    reply_count: Option[Long],
    metadata: Map[String, JsValue],
    sender_name: Option[String],
    set_online: Option[Boolean],
    team_id: Option[String]
  )

  // --- spray ---
  object JsonProtocol extends DefaultJsonProtocol /*with NullOptions*/
  import JsonProtocol._
  implicit val BroadcastFormat: RootJsonFormat[Broadcast] = jsonFormat4(Broadcast)
  implicit val DataFormat: RootJsonFormat[Data] = jsonFormat10(Data)
  implicit val EventMessageFormat: RootJsonFormat[EventMessage] = jsonFormat4(EventMessage)
  implicit val StatusMessageFormat: RootJsonFormat[StatusMessage] = jsonFormat2(StatusMessage)
  implicit val PostMessageFormat: RootJsonFormat[Post] = jsonFormat21(Post)
}
