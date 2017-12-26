package org.koraseg

import java.time.{Duration => JavaDuration}
import java.util.concurrent.TimeUnit
import javax.cache.expiry.{Duration => IgniteDuration}
import org.apache.spark.streaming.{Milliseconds, Duration => SparkDuration}
import org.joda.time.DateTime


package object botregistry {
  implicit def javaDurationToIgniteDuration(jDuration: JavaDuration): IgniteDuration = {
    new IgniteDuration(TimeUnit.SECONDS, jDuration.getSeconds)
  }

  implicit def javaDurationToSparkDuration(jDuration: JavaDuration): SparkDuration = {
    Milliseconds(jDuration.toMillis)
  }

  implicit val dateTimeOrdering = new Ordering[DateTime] {
    override def compare(x: DateTime, y: DateTime): Int = x.compareTo(y)
  }


}
