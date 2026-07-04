name := "wexler-data-platform"

scalaVersion := "2.12.11"

lazy val root = (project in file("."))
  .aggregate(logAnalyzerScala)
  .settings(
    publish / skip := true
  )

lazy val logAnalyzerScala = (project in file("jobs/log-analyzer-scala"))
