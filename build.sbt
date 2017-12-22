
name := "Final project"

version := "1.0"

scalaVersion := "2.10.7"



lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "io.spray" %% "spray-json" % "1.3.4",
    "joda-time" % "joda-time" % "2.9.9",
    "com.typesafe" % "config" % "1.3.2"
  ),
  scalaVersion := "2.10.7"
)


lazy val root = (project in file("."))
  .aggregate(common, processor, upstream)

lazy val common = (project in file("common"))
  .settings(commonSettings: _*)



lazy val processor = (project in file("processor"))
  .dependsOn(common)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % "1.3.4",
      "joda-time" % "joda-time" % "2.9.9",
      "com.typesafe" % "config" % "1.3.2",
      //remove transtitive spark deps
      ("org.apache.ignite" % "ignite-spark_2.10" % "2.1.0")
        .excludeAll(ExclusionRule("org.apache.hadoop"), ExclusionRule("org.apache.spark"))
        .exclude("commons-beanutils", "commons-beanutils-core")
        .exclude("commons-collections", "commons-collections")
        .exclude("commons-logging", "commons-logging")
    ),
    excludeDependencies ++= Seq("org.json4s" % "json4s-core_2.11"),
    scalaVersion := "2.10.7"
  )
  .settings(
    //spark deps
      libraryDependencies ++= Seq(
        "org.apache.spark" %% "spark-sql" % "2.1.0" % "provided",
        "org.apache.spark" %% "spark-streaming" % "2.1.0" % "provided",
        "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.1.0"
      ),
      libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    assemblyMergeStrategy in assembly := {
      case PathList(xs @ _*) if xs.last == "UnusedStubClass.class" => MergeStrategy.first
      case PathList("org", "scalatest", _*) => MergeStrategy.discard
      case x  =>
        val old = (assemblyMergeStrategy in assembly).value
        old(x)
    }
  )


lazy val upstream = (project in file("upstream"))
  .dependsOn(common)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.16"
  )


