package com.schmiegelow.converter

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import akka.http.javadsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import com.schmiegelow.converter.values._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.{ExecutionContextExecutor, Future}

object HttpToKafkaBridge extends HttpApp with App with JsonSupport with LazyLogging {

  implicit val system = ActorSystem("HttpToKafka")

  val decider: Supervision.Decider = {
    ex: Throwable =>
      logger.error(ex.getMessage, ex)
      Supervision.Restart
  }

  implicit val mat: ActorMaterializer = ActorMaterializer(
    ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  )

  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val config: Config = ConfigFactory.load()

  val host = config.getString("host.name")
  val port = config.getInt("host.port")

  val producerSettings = ProducerSettings(system, new ByteArraySerializer, new StringSerializer)
    .withBootstrapServers(config.getString("kafka.bootstrap.servers"))

  def kafkaStreamSink: Sink[String, Future[Done]] =
    Flow[String]
      .map { elem =>
        new ProducerRecord[Array[Byte], String](config.getString("kafka.topic"), elem)
      }
      .toMat(Producer.plainSink(producerSettings))(Keep.right)

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport = EntityStreamingSupport.json()

  override def routes: Route =
    path("send") {
      post {
        entity(as[UrlEntity]) { url =>
          logger.info(s"Received $url")
          onComplete {
            Source
              .single(url.url)
              .runWith(kafkaStreamSink)
          } { _ =>
            complete(ToResponseMarshallable((StatusCodes.Accepted, url.url)))
          }
        }
      }
    } ~
      path("send-bulk") {
        post {
          entity(as[UrlsEntity]) { url =>
            logger.info(s"Received ${url.urls.size} URLs")
            Source
              .fromIterator(() => url.urls.iterator)
              .runWith(kafkaStreamSink)
            complete(ToResponseMarshallable((StatusCodes.Accepted, s"received ${url.urls.size} URLs")))
          }
        }
      } ~ path("healthcheck") {
      complete(ToResponseMarshallable((StatusCodes.OK, "")))
    }

  HttpToKafkaBridge.startServer(host, port, system)
  system.terminate()
}
