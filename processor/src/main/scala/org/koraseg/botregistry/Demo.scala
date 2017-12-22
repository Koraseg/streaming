package org.koraseg.botregistry

import java.util
import java.util.concurrent.TimeUnit
import javax.cache.expiry.{CreatedExpiryPolicy, Duration, ModifiedExpiryPolicy}
import scala.math.Ordering.Implicits._
import org.apache.spark.streaming.kafka010._
import com.typesafe.config.ConfigFactory
import kafka.serializer.StringDecoder
import dateTimeOrdering._
import org.apache.ignite.configuration.{CacheConfiguration, IgniteConfiguration}
import org.apache.ignite.spark.IgniteContext
import org.apache.ignite.{Ignite, Ignition}
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.{Milliseconds, Minutes, Seconds, StreamingContext}
import org.joda.time.DateTime
import org.koraseg.botregistry.datamodel._
import spray.json._
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import scala.util.Try


object Demo extends App {



  val json =
    """
      |{
      |"type": "click",
      |"ip": "127.0.0.1",
      |"unix_time": "1500028835",
      |"url": "https://blog.griddynamics.com/in-stream-processing-service-blueprint"
      |}
    """.stripMargin


  val ud = json.parseJson.convertTo[UserData]
  println(ud.toJson)
  val conf = ConfigFactory.defaultApplication()
  val expirationTime = conf.getDuration("ignite.expiration_time")

  Ignition.setClientMode(true)
  val ignite = Ignition.start()
  val cacheConfig = new CacheConfiguration[String, DateTime]()
    .setName("bot_registry2")
    .setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(expirationTime))

  //val cache = ignite.getOrCreateCache(cacheConfig)
  val cache = ignite.cache[String, DateTime]("bot_registry2").withExpiryPolicy(ModifiedExpiryPolicy.factoryOf(expirationTime).create())
  val sparkSession = SparkSession.builder().appName("Ignite").master("local[4]").getOrCreate()
  val ic = new IgniteContext(sparkSession.sparkContext, () => new IgniteConfiguration())
  val rdd = ic.fromCache(cacheConfig)
  val kvRdd = sparkSession.sparkContext.parallelize(Seq("AAA" -> ud.unix_time.plusDays(2), "AAB" -> ud.unix_time.plusWeeks(1)))
  kvRdd.foreachPartition { it =>
    val ignite = ic.ignite()
    val cache = ignite.createCache(cacheConfig)
    it.foreach(tpl => cache.put(tpl._1, tpl._2))
  }

  //cache.put("aac", ud.unix_time.plusYears(2))
  println(cache.getAll(Set("AAA", "AAB", "aaa", "aab", "aac").asJava))
  Thread.sleep(12000)
  println(cache.getAll(Set("AAA", "AAB", "aaa", "aab", "aac").asJava))
  ignite.close()

}
object Demo2 extends App {
  val kafkaParams = Map[String, AnyRef](
    "bootstrap.servers" -> "localhost:9092",
    "key.deserializer" -> classOf[StringDeserializer],
    "value.deserializer" -> classOf[StringDeserializer],
    "group.id" -> "demo_1",
    "auto.offset.reset" -> "earliest",
    "enable.auto.commit" -> (false: java.lang.Boolean)
  )


  val conf = new SparkConf().setMaster("local[4]").setAppName("KafkaStreamingApp")
  val ssc = new StreamingContext(conf, Milliseconds(10000))
  val topics = Set("user_clicks")

  val inputStream = KafkaUtils.createDirectStream(ssc, PreferConsistent, Subscribe[String, String](topics, kafkaParams))
  val clickStream = inputStream.flatMap(record => Try(record.value().parseJson.convertTo[UserData]).toOption)
  val reduced = clickStream.map(ud => ud.ip -> 1).reduceByKeyAndWindow((x: Int, y: Int) => x + y, Minutes(10), Seconds(10))
  reduced.print()
  ssc.start()
  ssc.awaitTermination()
}