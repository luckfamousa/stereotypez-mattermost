
package de.stereotypez.mattermost.ws

import de.stereotypez.mattermost.ws.events._
import de.stereotypez.mattermost.ws.Protocol._
import de.stereotypez.mattermost.ws.Protocol.JsonProtocol._

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
    val txt = """{"event": "status_change", "data": {"status":"online","user_id":"4ac3xh974jgmffq8rdowxunjma"}, "broadcast": {"omit_users":null,"user_id":"zt3rb4qfi7bm3gfk31c97a3fih","channel_id":"","team_id":""}, "seq": 1}"""
    txt.parseJson.convertTo[WebsocketEvent]
  }

  
  @Test
  def test03(): Unit = {
    val txt = """{"status":"online","user_id":"zt3rb4qfi7bm3gfk31c97a3fih"}"""
    val data = txt.parseJson.convertTo[StatusChange.StatusChangeData]
    assertEquals(data.status, "online")
    assertEquals(data.user_id, "zt3rb4qfi7bm3gfk31c97a3fih")
  }

  @Test
  def test04(): Unit = {
    val txt = """{"event":"posted","data":{"channel_display_name":"@admin","channel_name":"4ac3xh974jgmffq8rdowxunjma__wb7dpg3tsfrimxcu8767ig55sh","channel_type":"D","mentions":"[\"4ac3xh974jgmffq8rdowxunjma\"]","post":"{\"id\":\"xo8gwpzsq78tur5soq47cjtskh\",\"create_at\":1633085267810,\"update_at\":1633085267810,\"edit_at\":0,\"delete_at\":0,\"is_pinned\":false,\"user_id\":\"wb7dpg3tsfrimxcu8767ig55sh\",\"channel_id\":\"3xqxgouhqjnm5k49qohjort7yc\",\"root_id\":\"\",\"parent_id\":\"\",\"original_id\":\"\",\"message\":\"oh there once was a hero named ragnar the red\",\"type\":\"\",\"props\":{\"disable_group_highlight\":true},\"hashtags\":\"\",\"pending_post_id\":\"wb7dpg3tsfrimxcu8767ig55sh:1633085267773\",\"reply_count\":0,\"last_reply_at\":0,\"participants\":null,\"is_following\":false,\"metadata\":{}}","sender_name":"@admin","set_online":true,"team_id":""},"broadcast":{"omit_users":null,"user_id":"","channel_id":"3xqxgouhqjnm5k49qohjort7yc","team_id":""},"seq":3}"""
    val event = txt.parseJson.convertTo[WebsocketEvent]
    event.data match {
      case data: Posted.PostData =>
        val post = data.post.parseJson.convertTo[Posted.Post]
        assertEquals(post.id, "xo8gwpzsq78tur5soq47cjtskh")
      case _=> assert(false)
    }
  }

}