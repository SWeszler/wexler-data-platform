scalaVersion := "2.12.11"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-sql" % "3.0.0" % "provided",
  "com.github.scopt" %% "scopt" % "4.0.0"
)

mainClass in assembly := Some("com.example.SessionAnalysis")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}
