package prg.koraseg.botregistry

import java.nio.file.{Files, Path, Paths}

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming._
import org.joda.time.DateTime
import org.koraseg.botregistry.datamodel._
import spray.json._
import scala.math.Ordering.Implicits._
import scala.collection.JavaConversions._
import org.koraseg.botregistry.dateTimeOrdering

import scala.annotation.tailrec
import scala.util.Try


object Stats {
  val zero: Stats = Stats(count = 0, min = new DateTime(Long.MaxValue), max = new DateTime(0L))

  def seqStatsOp(stats: Stats, dateTime: DateTime): Stats = Stats(
    count = stats.count + 1,
    min = stats.min.min(dateTime),
    max = stats.max.max(dateTime)
  )

  def combStatsOp(s1: Stats, s2: Stats): Stats = Stats(s1.count + s2.count, s1.min.min(s2.min), s1.max.max(s2.max))
}
case class Stats(count: Int, min: DateTime, max: DateTime) {
  import Stats._
  def |+|(other: Stats): Stats = combStatsOp(this, other)
}

object StreamingApp {

  def main(args: Array[String]): Unit = {
    import Stats._

    val checkpointDirectory = "./spark-checkpoint"
    clearDir(Paths.get(checkpointDirectory))
    val kafkaParams = Map[String, AnyRef](
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "demo_1",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    val ssc = StreamingContext.getOrCreate(checkpointDirectory, () => createStreamingContext(checkpointDirectory))

    val topics = Set("user_clicks")

    val inputStream = KafkaUtils.createDirectStream(ssc, PreferConsistent, Subscribe[String, String](topics, kafkaParams))
    val clickStream = inputStream.flatMap(record => Try(record.value().parseJson.convertTo[UserData]).toOption)

    /*  val reduced = clickStream.map(ud => ud.ip -> ud.unix_time)
        .transform(_.aggregateByKey(Stats.zero)(seqStatsOp, combStatsOp).sortBy(_._2.count, ascending = false))
        */

    //use a fact that the same key always come to the same partition because of kafka topic configuration
    val combined = clickStream.map(ud => ud.ip -> ud.unix_time)
      .transform(_.combineByKey(dt => Stats(1, dt, dt), seqStatsOp, combStatsOp))

    val statStream = combined.reduceByKeyAndWindow((s1: Stats, s2: Stats) => s1 |+| s2, Seconds(60), Seconds(10))
      .transform(_.sortBy(arg => arg._2.count, ascending = false))

    statStream.print()
    ssc.start()
    ssc.awaitTermination()

  }

  def clearDir(path: Path): Unit = Files.newDirectoryStream(path).foreach {
    case path if Files.isDirectory(path) => clearDir(path)
    case file => Files.delete(file)
  }

  def createStreamingContext(checkpointDir: String, batchInterval: Duration = Seconds(10)): StreamingContext = {
    val conf = new SparkConf().setMaster("local[4]").setAppName("KafkaStreamingApp")
    val ssc = new StreamingContext(conf, batchInterval)
    ssc.checkpoint(checkpointDir)
    ssc
  }

}
