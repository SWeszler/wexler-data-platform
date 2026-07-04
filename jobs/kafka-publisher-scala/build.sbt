name := "kafka-publisher-scala"

scalaVersion := "2.12.17"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % "3.5.6" % "provided",
  "com.github.scopt" %% "scopt" % "4.0.1"
)

mainClass in assembly := Some("com.example.KafkaPublisher")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
