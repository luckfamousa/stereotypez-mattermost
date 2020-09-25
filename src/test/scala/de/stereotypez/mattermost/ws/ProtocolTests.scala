package de.stereotypez.mattermost.ws

import de.stereotypez.mattermost.ws.Protocol.{Broadcast, Data, EventMessage, StatusMessage}
import org.junit.Test
import org.junit.Assert._
import spray.json._

class ProtocolTests {

  @Test
  def test01(): Unit =  {
    val txt = """{"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}"""
    txt.parseJson.convertTo[Broadcast]
  }

  @Test
  def test02(): Unit =  {
    val txt = """{"event": "status_change", "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""
    txt.parseJson.convertTo[EventMessage]
  }

  @Test
  def test03(): Unit =  {
    val txt = """{"event": "status_change", "data": null, "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""
    txt.parseJson.convertTo[EventMessage]
  }

  @Test
  def test04(): Unit =  {
    val txt = """{"status":"online","user_id":"zt3rb4qfi7bm3gfk31c97a3fih"}"""
    val data = txt.parseJson.convertTo[Data]
    assertEquals(data.status, Some("online"))
    assertEquals(data.user_id, Some("zt3rb4qfi7bm3gfk31c97a3fih"))
    assertEquals(data.team_id, None)
  }

  @Test
  def test07(): Unit =  {

    val txt = """{"event": "status_change", "data": {"status":"online","user_id":"zt3rb4qfi7bm3gfk31c97a3fih"}, "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""

    txt.parseJson.asJsObject match {
      case ast if ast.fields.contains("event") =>
        val event = ast.convertTo[EventMessage]
        assertEquals(event.data.flatMap(_.status), Some("online"))
        assertEquals(event.data.flatMap(_.post), None)

      case ast if ast.fields.contains("status") =>
        ast.convertTo[StatusMessage]
      case _ => throw new MatterMostWsProtocolException("Unknown message type:\n" + txt.parseJson.prettyPrint)
    }
  }

}
