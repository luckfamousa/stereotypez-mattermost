//user_removed

package de.stereotypez.mattermost.ws.events

import spray.json._
import de.stereotypez.mattermost.ws.Protocol
import Protocol._
import Protocol.JsonProtocol._

object UserRemoved extends EventParser("user_removed"){
    def convert(js: JsValue): WSEventData = js.convertTo[UserRemovedData]
    case class UserRemovedData(
        remover_id: String,
        user_id: String,
    ) extends WSEventData
    implicit val userRemovedDataFormat: RootJsonFormat[UserRemovedData] = jsonFormat2(UserRemovedData)
}