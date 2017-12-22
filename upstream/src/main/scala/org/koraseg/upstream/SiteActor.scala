package org.koraseg.upstream

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging}
import org.joda.time.DateTime
import org.koraseg.botregistry.datamodel.{Site, UserData}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try


object SiteActor {
  case object FlushBuffer
  val counter = new AtomicLong()
}

class SiteActor(site: Site, spoolDir: Path, tempDir: Path) extends Actor with ActorLogging {
  import SiteActor._
  import context.dispatcher

  val idCounter = new AtomicLong()
  val uuid = UUID.randomUUID()
  val ioDispatcher: ExecutionContext = context.system.dispatchers.lookup("io-dispatcher")
  val buffer = ArrayBuffer[String]()

  override def preStart(): Unit = {
    context.system.scheduler.schedule(30 seconds, 30 seconds, self, FlushBuffer)
  }

  override def receive: Receive = {
    case ud @ UserData(event, ip, unix_time, url) if url == site =>
      log.debug("Receiving a new click")
      buffer += ud.toJson.compactPrint

    case FlushBuffer =>
      flushBuffer()


    case msg =>
      log.warning(s"Unexpected message: $msg")

  }

  private def flushBuffer(): Future[Unit] = {
    val flushArray = new Array[String](buffer.size)
    buffer.copyToArray(flushArray)
    buffer.clear()
    val res = Future(
      {
        Thread.sleep(randomDelay(10 seconds).toMillis)
        val tmpPath = Paths.get(tempDir.toString, s"${site}_${uuid}_${idCounter.incrementAndGet()}")
        val pw = new PrintWriter(tmpPath.toString)
        val start = System.currentTimeMillis()
        pw.write(flushArray.mkString("\n"))
        pw.flush()
        pw.close()
        val end = System.currentTimeMillis()
        log.info(s"${end - start} seconds for flushing ${flushArray.size} records")
        log.info(s"${counter.addAndGet(flushArray.size)} messages are moved to the spool directory")
        Files.copy(tmpPath, Paths.get(spoolDir.toString, tmpPath.getFileName.toString))
        Files.delete(tmpPath)
      }
    )(ioDispatcher)
    res.onFailure {
      case t => log.error(t, "Error while moving a file to the spool directory")
    }
    res
  }
  override def postStop(): Unit = {
    log.info(s"Stopping the actor - so data needs to be flushed for $site actor")
    Await.result(flushBuffer(), 20 seconds)
  }

}