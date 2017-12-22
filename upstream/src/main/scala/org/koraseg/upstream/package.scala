package org.koraseg

import java.util.concurrent.ThreadLocalRandom
import org.koraseg.botregistry.datamodel._
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

package object upstream {

 lazy val mainThreadRandom = new Random()

  def randomIp: Ip = s"${mainThreadRandom.nextInt(256)}.${mainThreadRandom.nextInt(256)}.${mainThreadRandom.nextInt(256)}.${mainThreadRandom.nextInt(256)}"

  def randomDelay(maxDuration: FiniteDuration = 30 seconds): FiniteDuration =
    ThreadLocalRandom.current().nextLong(maxDuration.toMillis) milliseconds
}
