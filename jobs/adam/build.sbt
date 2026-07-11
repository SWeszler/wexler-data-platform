scalaVersion := "2.12.17"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.6" % "provided",
  "org.apache.spark" %% "spark-sql" % "3.5.6" % "provided",
  "org.bdgenomics.adam" %% "adam-core-spark3" % "1.0.1",
  "com.github.scopt" %% "scopt" % "4.1.0"
)

assembly / mainClass := Some("com.wexler.adam.AdamPipeline")

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _ @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}
