package com.github.jodersky.flow.samples.terminal

import com.github.jodersky.flow.Serial
import com.github.jodersky.flow.Serial.Close
import com.github.jodersky.flow.Serial.Closed
import com.github.jodersky.flow.Serial.CommandFailed
import com.github.jodersky.flow.Serial.Event
import com.github.jodersky.flow.Serial.Opened
import com.github.jodersky.flow.Serial.Received
import com.github.jodersky.flow.Serial.Write
import com.github.jodersky.flow.SerialSettings

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.io.IO
import akka.util.ByteString

class Terminal(port: String, settings: SerialSettings) extends Actor with ActorLogging {
  import Terminal._
  import context._

  val reader = actorOf(Props[ConsoleReader])
  
  log.info(s"Requesting manager to open port: ${port}, baud: ${settings.baud}")
  IO(Serial) ! Serial.Open(port, settings)
  
  override def postStop() = {
    system.shutdown()
  }

  def receive = {
    case CommandFailed(cmd, reason) => {
      log.error(s"Connection failed, stopping terminal. Reason: ${reason}")
      context stop self
    }
    case Opened(port) => {
      log.info(s"Port ${port} is now open.")
      val operator = sender
      context become opened(operator)
      context watch operator
      reader ! ConsoleReader.Read
    }
  }

  def opened(operator: ActorRef): Receive = {
    
    case Received(data) => {
      log.info(s"Received data: ${formatData(data)}")
    }
    
    case Wrote(data) => log.info(s"Wrote data: ${formatData(data)}")

    case Closed => {
      log.info("Operator closed normally, exiting terminal.")
      context unwatch operator
      context stop self
    }
    
    case Terminated(`operator`) => {
      log.error("Operator crashed, exiting terminal.")
      context stop self
    }

    case ConsoleReader.EOT => {
      log.info("Initiating close.")
      operator ! Close
    }

    case ConsoleReader.ConsoleInput(input) => {
      val data = ByteString(input.getBytes)
      operator ! Write(data, length => Wrote(data.take(length)))
      reader ! ConsoleReader.Read
    }
  }

  

}

object Terminal {
  case class Wrote(data: ByteString) extends Event
  
  def apply(port: String, settings: SerialSettings) = Props(classOf[Terminal], port, settings)
  
  private def formatData(data: ByteString) = data.mkString("[", ",", "]") + " " + (new String(data.toArray, "UTF-8"))
  
}