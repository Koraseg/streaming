package org.koraseg

import java.nio.file.{Files, Path}
import java.time.{Duration => JavaDuration}
import java.util.concurrent.TimeUnit
import javax.cache.expiry.{Duration => IgniteDuration}
import org.joda.time.DateTime
import scala.collection.JavaConversions._

package object botregistry {
  implicit def javaDurationToIgniteDuration(jDuration: JavaDuration): IgniteDuration = {
    new IgniteDuration(TimeUnit.SECONDS, jDuration.getSeconds)
  }

  implicit val dateTimeOrdering = new Ordering[DateTime] {
    override def compare(x: DateTime, y: DateTime): Int = x.compareTo(y)
  }


}
