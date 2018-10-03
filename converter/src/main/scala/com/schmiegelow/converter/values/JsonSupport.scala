package com.schmiegelow.converter.values

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val urlFormat: RootJsonFormat[UrlEntity] = jsonFormat1(UrlEntity)
  implicit val urlsFormat: RootJsonFormat[UrlsEntity] = jsonFormat1(UrlsEntity)
}
