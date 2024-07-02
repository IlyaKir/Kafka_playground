ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    libraryDependencies += "dev.zio" %% "zio-kafka" % "2.7.4",
//    libraryDependencies += "org.apache.kafka" % "kafka-clients" % "3.7.0",
    libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.36",
    libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % "4.0.0-M11",
    libraryDependencies += "com.softwaremill.sttp.client4" %% "async-http-client-backend" % "4.0.0-M11",
    libraryDependencies += "com.softwaremill.sttp.client4" %% "zio" % "4.0.0-M11",
    name := "kafka_pg"
  )
