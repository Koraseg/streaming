package org.koraseg.upstream

import java.nio.file.{Files, Paths}

import akka.actor.{ActorSystem, PoisonPill, Props}
import RandomSiteChooser._
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.koraseg.botregistry.datamodel.Ip

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.Await

object UpstreamApp extends App {
  val conf = ConfigFactory.load()
  val averageClickRate = conf.getInt("upstream.averageClickRate")
  val usersNumber = conf.getInt("upstream.usersNumber")
  val spoolDir = Paths.get(conf.getString("upstream.spoolDir"))
  val tempDir = Paths.get(conf.getString("upstream.tempDir"))


  implicit val actorSystem = ActorSystem("UpstreamActorSystem")

  val userIps = mutable.Set[Ip]()
  while (userIps.size < usersNumber) userIps += randomIp

  //create directories when it is necessary
  if (Files.notExists(tempDir)) Files.createDirectory(tempDir)
  if (Files.notExists(spoolDir)) Files.createDirectory(spoolDir)


  val siteActorRefs = sites.map(site => site -> actorSystem.actorOf(Props(new SiteActor(site, spoolDir, tempDir)))).toMap
  val userActors = userIps.map { ip =>
    val averageInterval = ((1d / averageClickRate) * 1000).toInt
    val intervalMillis = averageInterval / 2 + mainThreadRandom.nextInt(averageInterval)
    actorSystem.actorOf(Props(new UserActor(ip, intervalMillis, siteActorRefs)))
  }


  sys.addShutdownHook {

    actorSystem.shutdown()
    Files.newDirectoryStream(tempDir).iterator().asScala.foreach(Files.delete)
    actorSystem.awaitTermination(30 seconds)
  }


}
