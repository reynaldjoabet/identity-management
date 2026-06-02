import Dependencies._

ThisBuild / scalaVersion := "3.3.7"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-no-indent",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-explain", // + actionable error messages
  "-source:3.3", // + pin source level, no silent drift
  // "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Ysafe-init",
  "-language:strictEquality", // + catch nonsensical == (Money vs String, etc.)
  "-Ykind-projector",
  "-Xmax-inlines",
  "64"
)

lazy val root = (project in file("."))
  .settings(
    name := "identity-management",
    libraryDependencies ++= Seq(iron, munit)
  )

outputStrategy := Some(StdoutOutput)
