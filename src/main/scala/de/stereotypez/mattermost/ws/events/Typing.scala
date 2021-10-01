package de.stereotypez.mattermost.ws.events

import spray.json._
import de.stereotypez.mattermost.ws.Protocol
import Protocol._
import Protocol.JsonProtocol._

object Typing extends EventParser("typing"){
    def convert(js: JsValue): WSEventData = js.convertTo[TypingData]
    case class TypingData(
        user_id: String,
        parent_id: String,
    ) extends WSEventData
    implicit val TypingDataFormat: RootJsonFormat[TypingData] = jsonFormat2(TypingData)
}