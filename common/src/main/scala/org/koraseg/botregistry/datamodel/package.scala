package org.koraseg.botregistry

import org.joda.time.DateTime
import spray.json.{DefaultJsonProtocol, JsNumber, JsString, JsValue, JsonFormat, ProductFormats, RootJsonFormat}

package object datamodel extends DefaultJsonProtocol {

  type Site = String
  type Ip = String

  object Event {
    implicit object eventFormat extends JsonFormat[Event] {
      def write(e: Event) = e match {
        case Click => JsString("click")
        case Impression => JsString("impression")
      }

      def read(value: JsValue): Event =
        value match {
          case JsString("click") => Click
          case JsString("impression") => Impression
        }
    }

  }


  sealed trait Event
  case object Click extends Event
  case object Impression extends Event

  //adapt to strange representation of unix time in the input
  implicit object dateTimeConverter extends JsonFormat[DateTime] {
    override def write(dt: DateTime): JsValue = JsString((dt.getMillis / 1000).toString)
    override def read(json: JsValue): DateTime = json match {
      case JsString(uxTimeAsString) => new DateTime(uxTimeAsString.toLong * 1000)
    }
  }

  object UserData {
    implicit val userDataFormat: JsonFormat[UserData] = jsonFormat4(UserData.apply)
  }
  case class UserData(`type`: Event, ip: String, unix_time: DateTime, url: String)





}