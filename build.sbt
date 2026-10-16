import Dependencies._

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / crossScalaVersions := Seq("3.3.8", "3.9.0")

ThisBuild / scalacOptions := Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-Werror",
  // "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-language:strictEquality",
  "-Xcheck-macros",
  "-Xmax-inlines:64"
)

lazy val root = (project in file("."))
  .settings(
    name := "identity-management",
    libraryDependencies ++= Seq(
      iron,
      munit,
      catsEffect,
      http4sDsl,
      emberServer,
      emberClient,
      http4sCirce,
      jsoniter,
      jsoniterMacros,
      circeCore,
      circeGeneric,
      ironJsoniter,
      fs2,
      fs2Kafka,
      "tools.jackson.core" % "jackson-databind" % "3.2.3",
      "tools.jackson.core" % "jackson-core" % "3.2.3",
      vault,
      slf4j,
      nimbusJoseJwt,
      nimbusOauth2Oidc,
      munitCatsEffect,
      munit
    )
  )

outputStrategy := Some(StdoutOutput)
