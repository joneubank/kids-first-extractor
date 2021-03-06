scalaVersion := "2.11.8"

name := "kids-first-extractor"
organization := "com.joneubank.kf"
version := "0.1"

fork := true

//Spark
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "2.2.0",
  "org.apache.spark" %% "spark-sql" % "2.2.0"
)

// HMAC
libraryDependencies += "com.gu" %% "hmac-headers" % "1.0"

// JSON
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"

// YAML - using jackson - for config file
libraryDependencies += "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.9.3"
