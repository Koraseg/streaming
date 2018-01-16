package org.koraseg.botregistry

import java.util.logging.Logger
import javax.cache.expiry.ModifiedExpiryPolicy

import com.typesafe.config.ConfigFactory
import org.apache.ignite.configuration.{CacheConfiguration, IgniteConfiguration}
import org.apache.ignite.spark.{IgniteContext, IgniteRDD}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.StreamingContextState._
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.{CanCommitOffsets, HasOffsetRanges, KafkaUtils}
import org.apache.spark.streaming.{Seconds, StreamingContext, Time}
import org.joda.time.DateTime
import org.koraseg.botregistry.datamodel.{UserData, _}
import spray.json._

import scala.math.Ordering.Implicits._
import scala.reflect.ClassTag
import scala.util.Try


object Stats {

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

  def rate: Double = count.toDouble / (max.getMillis - min.getMillis)
}



trait ProcessingModule extends Serializable {

  lazy val conf = ConfigFactory.load()

  lazy val kafkaParams = Map[String, AnyRef](
    "bootstrap.servers" -> conf.getString("kafka.bootstrap.servers"),
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[StringDeserializer],
    "group.id" -> conf.getString("kafka.group.id"),
    "auto.offset.reset" -> "latest",
    "enable.auto.commit" -> (false: java.lang.Boolean)
  )
  val checkPointDir = conf.getString("spark.checkpoint_dir")


  def start(): Unit = {
    val ssc = StreamingContext.getOrCreate(checkPointDir, () => createStreamingContext())
    sys.addShutdownHook({
      if (ssc.getState() != STOPPED) {
        ssc.stop(stopGracefully = false, stopSparkContext = true)
      }
    })
    ssc.start()
    ssc.awaitTermination()

  }


  private def isBot(stats: Stats): Boolean = {
    if (stats.count < 1000) return false
    val threshold = conf.getInt("app.bot.window_threshold")
    val interval = conf.getDuration("app.bot.window_interval")
    val rate = threshold.toDouble / interval.toMillis
    stats.rate > rate
  }

  private def createStreamingContext(): StreamingContext = {
    import Stats._

    val sparkConf = new SparkConf()
      .setMaster(conf.getString("spark.master"))
      .setAppName(conf.getString("spark.app_name"))
    val cacheName = conf.getString("app.cache_name")
    val expirationTime = conf.getDuration("app.expiration_time")
    val cacheConfig = new CacheConfiguration[String, DateTime]()
      .setName(cacheName)
      .setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(expirationTime))
    val batchInterval = conf.getDuration("spark.batch_interval")
    val ssc = new StreamingContext(sparkConf, batchInterval)
    val topics = Set(conf.getString("kafka.topic"))

    val inputStream = KafkaUtils.createDirectStream(ssc, PreferConsistent, Subscribe[String, String](topics, kafkaParams))
    val sc = ssc.sparkContext
    val ic = new IgniteContext(sc, () => new IgniteConfiguration())
    val cache = ic.fromCache(cacheConfig)
    val clickStream = inputStream
      .flatMap(record => Try(record.value().parseJson.convertTo[UserData]).toOption)
      .transform { clicks =>
        clicks.mapPartitions { it =>
          val bots = ic.ignite().getOrCreateCache(cacheConfig)
          it.filterNot(ud => bots.containsKey(ud.ip))
        }
      }


    /* use a fact that the same key always come to the same partition because of kafka topic configuration
      if the configuration was arbitrary reduceByKey method should be used then
     */
    val combined = clickStream.map(ud => ud.ip -> ud.unix_time)
      .transform(_.combineByKey(dt => Stats(1, dt, dt), seqStatsOp, combStatsOp))

    val windowInterval = conf.getDuration("app.bot.window_interval")
    val statStream = combined
      .reduceByKeyAndWindow((s1: Stats, s2: Stats) => s1 |+| s2, windowInterval, batchInterval)
      .transform { rdd =>
        //filtering non-bot data to prevent save of bots detected on previous batches
        rdd.mapPartitions { it =>
          val bots = ic.ignite().getOrCreateCache(cacheConfig)
          it.filterNot(pair => bots.containsKey(pair._1))
        }
      }


    /* commit offsets only after ignite update to ensure at least once delivery semantics
      updates in ignite are idempotent since overwrite flag is set to false
     */
    statStream.foreachRDD { stats =>
      cache.savePairs(stats.filter({ case (_, stats) => isBot(stats) }).mapValues(_.max), overwrite = false)
    }

    inputStream.foreachRDD { rdd =>
      val offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      inputStream.asInstanceOf[CanCommitOffsets].commitAsync(offsetRanges)
    }


    if (conf.getBoolean("app.is_debug_mode")) {
      val cacheStream = new CacheIgniteStream(ssc, cacheConfig).map(_._1)
      val top = statStream.transform(_.sortBy(arg => arg._2.count, ascending = false))
      top.foreachRDD { rdd =>
        val logger = Logger.getLogger(classOf[ProcessingModule].getName)
        logger.info("Top five most active IPs:")
        val topFive = rdd.take(5)
        if (topFive.size > 0) logger.info(topFive.mkString("\n")) else logger.info("There is no stats data in the stream now.")
      }
      cacheStream.foreachRDD { (rdd, time) =>
        val logger = Logger.getLogger(classOf[ProcessingModule].getName)
        logger.info(s"Current bot IPs (Time is ${new DateTime(time.milliseconds)}):")
        val bots = rdd.collect()
        if (bots.size > 0) logger.info(bots.mkString("\n")) else logger.info("There are no bots in the registry now.")
      }
    }

    ssc.checkpoint(checkPointDir)
    ssc.remember(Seconds(0L))

    ssc
  }
}

class CacheIgniteStream[K : ClassTag, V : ClassTag](_ssc : StreamingContext, cacheConfiguration: CacheConfiguration[K, V]) extends InputDStream[(K, V)](_ssc) {
  @transient @volatile private[this] var _ic: IgniteContext = null
  override def compute(validTime: Time): Option[RDD[(K, V)]] = {
    Some(new IgniteRDD[K, V](igniteContext, cacheConfiguration.getName, cacheConfiguration, false))
  }

  override def stop(): Unit = {}

  override def start(): Unit = {}

  private def igniteContext(): IgniteContext = {
    if (_ic == null) {
      this.synchronized {
        if (_ic == null) {
          _ic = new IgniteContext(context.sparkContext, () => new IgniteConfiguration())
        }
      }
      _ic
    } else {
      _ic
    }
  }
}
