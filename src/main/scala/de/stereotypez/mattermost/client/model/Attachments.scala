package de.stereotypez.mattermost.client.model

import java.util
import scala.jdk.CollectionConverters._

case class Action(name: String, url: String, context: Map[String, String]) {
  def forMM: util.Map[String, AnyRef] = {
    Map(
      "name" -> name,
      "integration" -> Map(
        "url" -> url,
        "context" -> context.map(t => (t._1, t._2.asInstanceOf[AnyRef])).asJava
      ).asJava
    ).asJava
  }
}

case class Actions(actions: Seq[Action]) {
  def forMM: util.List[util.Map[String, AnyRef]] = actions.map(_.forMM).asJava
}

case class Attachment(params: Option[Map[String, String]] = None, actions: Option[Actions] = None) {

  def add(action: Action): Attachment = {
    Attachment(
      params,
      Some(
        actions
          .map(actions => Actions(actions.actions :+ action))
          .getOrElse(Actions(Seq(action)))
      )
    )
  }

  def forMM: util.Map[String, AnyRef] = {
    ((params, actions) match {
      case (Some(p), Some(a)) =>
        p.map(t => (t._1, t._2.asInstanceOf[AnyRef])) ++ Map("actions" -> a.forMM.asInstanceOf[AnyRef])

      case (Some(p), None) =>
        p.map(t => (t._1, t._2.asInstanceOf[AnyRef]))

      case (None, Some(a)) =>
        Map("actions" -> a.forMM.asInstanceOf[AnyRef])

      case _ => Map.empty[String, AnyRef]
    }).asJava
  }
}

case class Attachments(attachments: Seq[Attachment] = Seq.empty) {
  def add(attachment: Attachment): Attachments = Attachments(attachments :+ attachment)

  def forMM: util.List[util.Map[String, AnyRef]] = {
    attachments.map(_.forMM).asJava
  }
}

class Test {

  Attachments()
    .add(Attachment(Some(Map("pretext" -> "This is the attachment pretext.")))
      .add(Action("Get Studies", "http://chat.ume.de", Map("action" -> "Do something")))
      .add(Action("Get Series", "http://chat.ume.de", Map("action" -> "Do something"))))

}
