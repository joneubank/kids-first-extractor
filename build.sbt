scalaVersion := "2.12.3"

name := "kids-first-extractor"
organization := "com.joneubank.kf"
version := "0.1"

libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.2.0"
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.2.0"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "{latestVersion}"
