scalaVersion := "2.11.8"

name := "kids-first-extractor"
organization := "com.joneubank.kf"
version := "0.1"

//Spark
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.2.0",
  "org.apache.spark" %% "spark-sql" % "2.2.0"
)

// HMAC
libraryDependencies += "com.gu" %% "hmac-headers" % "1.0"

//JSON
libraryDependencies += "org.json4s" %% "json4s-jackson" % "{latestVersion}"
