package org.koraseg.upstream

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef}
import org.joda.time.DateTime
import org.koraseg.botregistry.datamodel.{Click, Ip, Site, UserData}

import scala.concurrent.ExecutionContext


object UserActor {
  case object Fire
  val counter = new AtomicLong()
}

class UserActor(ip: Ip, intervalMillis: Int, siteActors: Map[Site, ActorRef]) extends Actor with RandomSiteChooser with ActorLogging {
  import UserActor._
  import context.dispatcher
  require(intervalMillis > 0)


  def runnableFire: Runnable = new Runnable {
    override def run(): Unit = {
      self ! Fire
      context.system.scheduler.scheduleOnce(randomDelay(2 * intervalMillis millis), runnableFire)
    }
  }

  override def preStart(): Unit = {
    context.system.scheduler.scheduleOnce(randomDelay(2 * intervalMillis millis), runnableFire)
    //context.system.scheduler.schedule(randomDelay(), intervalMillis milliseconds, self, Fire)
  }


  override def receive: Receive = {
    case Fire =>
      val site = choose()
      siteActors(site) ! UserData(Click, ip, DateTime.now(), site)
      if (counter.incrementAndGet() % 10000 == 0) {
        log.info(s"${counter.get()} messages are sent by users")
      }

    case msg =>
      log.warning(s"Unexpected message: $msg")
  }
}