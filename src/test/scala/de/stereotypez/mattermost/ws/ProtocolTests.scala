package de.stereotypez.mattermost.ws

import de.stereotypez.mattermost.ws.Protocol.{Broadcast, Data, EventMessage, Post, StatusMessage}
import spray.json._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._

class ProtocolTests extends AnyFunSuite {
  test("test-01") {
    val txt = """{"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}"""
    txt.parseJson.convertTo[Broadcast]
  }

  test("test-02") {
    val txt = """{"event": "status_change", "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""
    txt.parseJson.convertTo[EventMessage]
  }

  test("test-03") {
    val txt = """{"event": "status_change", "data": null, "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""
    txt.parseJson.convertTo[EventMessage]
  }

  test("test-04"){
    val txt = """{"status":"online","user_id":"zt3rb4qfi7bm3gfk31c97a3fih"}"""
    val data = txt.parseJson.convertTo[Data]
    data.status should equal(Some("online"))
    data.user_id should equal(Some("zt3rb4qfi7bm3gfk31c97a3fih"))
    data.team_id should equal(None)
  }

  test("test-05"){
    val txt = """{"id":"r36ktcdsuffq7x67zcstuswr5h","create_at":1601328848778,"update_at":1601328848778,"edit_at":0,"delete_at":0,"is_pinned":false,"user_id":"9nf7d8jrp3r88qkhownk3b1keo","channel_id":"ix8sg36eppbs9yuuqnqottrwme","root_id":"","parent_id":"","original_id":"","message":"Wilhelm Nensa","type":"","props":{"disable_group_highlight":true},"hashtags":"","pending_post_id":"9nf7d8jrp3r88qkhownk3b1keo:1601328851756","reply_count":0,"metadata":{}}"""
    txt.parseJson.convertTo[Post]
  }

  test("test-07") {

    val txt = """{"event": "status_change", "data": {"status":"online","user_id":"zt3rb4qfi7bm3gfk31c97a3fih"}, "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""

    txt.parseJson.asJsObject match {
      case ast if ast.fields.contains("event") =>
        val event = ast.convertTo[EventMessage]
        event.data.flatMap(_.status) should equal(Some("online"))
        event.data.flatMap(_.post) should equal(None)

      case ast if ast.fields.contains("status") =>
        ast.convertTo[StatusMessage]
      case _ => throw new MatterMostWsProtocolException("Unknown message type:\n" + txt.parseJson.prettyPrint)
    }
  }

}
