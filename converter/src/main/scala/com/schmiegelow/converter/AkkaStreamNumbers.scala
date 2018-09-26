package com.schmiegelow.converter

import akka._
import akka.actor._
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent._

object AkkaStreamNumbers {

  implicit val system: ActorSystem = ActorSystem("Sys")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val numbers: Range.Inclusive = 1 to 1000

  //We create a Source that will iterate over the number sequence
  val numberSource: Source[Int, NotUsed] = Source.fromIterator(() => numbers.iterator)

  //Only let pass even numbers through the Flow
  val isEvenFlow: Flow[Int, Int, NotUsed] = Flow[Int].filter(num => num % 2 == 0)

  //Create a Source of even random numbers by combining the random number Source with the even number filter Flow
  val evenNumbersSource: Source[Int, NotUsed] = numberSource.via(isEvenFlow)

  //A Sink that will write its input onto the console
  val consoleSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

  def main(args: Array[String]) {
    evenNumbersSource.runWith(consoleSink)
  }
}
