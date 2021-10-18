package de.stereotypez.mattermost.ws

import de.stereotypez.mattermost.ws.events
import spray.json._

object Protocol {
  abstract class EventParser(val eventType: String){
    def convert(js: JsValue): WSEventData
  }

  //TODO: perhaps retrieve this via reflection?
  val parsers: List[EventParser] = List(
    events.Posted, events.Typing, events.UserRemoved, events.StatusChange
  )

  object JsonProtocol extends DefaultJsonProtocol
  import JsonProtocol._

  type JsonEncodedString = String
  trait WebsocketMessage

  case class WebsocketResponse(
    status: String,
    seq_reply: Long,
  ) extends WebsocketMessage
  implicit val WSResponseFormat: RootJsonFormat[WebsocketResponse] = jsonFormat2(WebsocketResponse)

  trait WSEventData
  case class WebsocketEvent(
    event: String,
    seq: Long,
    broadcast: Broadcast,
    data: WSEventData
  ) extends WebsocketMessage

  case class Broadcast(
    omit_users: Option[JsValue],
    user_id: String,
    channel_id: String,
    team_id: String
  )
  implicit val broadCastFormat: RootJsonFormat[Broadcast] = jsonFormat4(Broadcast)

  //incase an event is either unimplemented or unknown in general
  case class FallbackData(
    json: JsObject
  ) extends WSEventData

  implicit object WSEventFormat extends RootJsonFormat[WebsocketEvent]{
    def read(json: JsValue): WebsocketEvent = {
      val fields = json.asJsObject.fields
      val event = fields.get("event").get.convertTo[String]
      val jsonData= fields.get("data").get
      val data = parsers.find(parser=> parser.eventType==event).flatMap(parser=>Some(parser.convert(jsonData))).getOrElse(FallbackData(jsonData.asJsObject))
      WebsocketEvent(
        event = event,
        seq = fields.get("seq").get.convertTo[Long],
        data = data,
        broadcast = fields.get("broadcast").get.convertTo[Broadcast]
      )
    }
    
    def write(obj: WebsocketEvent): JsValue = ???
    
  }
}
