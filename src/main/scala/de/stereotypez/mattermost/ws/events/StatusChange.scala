package de.stereotypez.mattermost.ws.events

import spray.json._
import de.stereotypez.mattermost.ws.Protocol
import Protocol._
import Protocol.JsonProtocol._

object StatusChange extends EventParser("status_change"){
    def convert(js: JsValue): WSEventData = js.convertTo[StatusChangeData]
    case class StatusChangeData(
        status: String,
        user_id: String,
    ) extends WSEventData
    implicit val statusChangeFormat: RootJsonFormat[StatusChangeData] = jsonFormat2(StatusChangeData)
}