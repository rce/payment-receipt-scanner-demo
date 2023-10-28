ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"

val log4j2Version = "2.20.0"
val jacksonVersion = "2.15.1"
val awsSdkVersion = "2.21.10"

lazy val root = (project in file("."))
  .settings(
    name := "server",
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    assembly / assemblyMergeStrategy := {
      //case PathList(ps @ _*) if ps.last == "Log4j2Plugins.dat" => Log4j2MergeStrategy.plugincache
      case x => MergeStrategy.first
    },
    libraryDependencies ++= Seq(
      // JSON
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
      // AWS
      "software.amazon.awssdk" % "sts" % awsSdkVersion,
      "software.amazon.awssdk" % "s3" % awsSdkVersion,
      "software.amazon.awssdk" % "textract" % awsSdkVersion,
      "software.amazon.awssdk" % "secretsmanager" % awsSdkVersion,
      // AWS Lambda
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.3",
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.3",
      // File upload
      "commons-fileupload" % "commons-fileupload" % "1.5",
      // Test
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
    )
  )