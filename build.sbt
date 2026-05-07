ThisBuild / tlBaseVersion := "0.2"
ThisBuild / organization := "org.polyvariant"
ThisBuild / organizationName := "Polyvariant"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(tlGitHubDev("kubukoz", "Jakub Kozłowski"))

ThisBuild / githubWorkflowPublishTargetBranches := Seq(
  RefPredicate.Equals(Ref.Branch("main")),
  RefPredicate.StartsWith(Ref.Tag("v")),
)

ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))

val scala212 = "2.12.21"
val scala3 = "3.8.3"

ThisBuild / scalaVersion := scala212
ThisBuild / crossScalaVersions := Seq(scala212, scala3)
ThisBuild / tlJdkRelease := None
ThisBuild / tlFatalWarnings := false

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

val scriptedTestIds = List(
  "fmt/format-rewrite",
  "fmt/format-check",
  "fmt/format-src-smithy",
  "codegen/sample-traits",
  "codegen/namespace-restriction",
)

ThisBuild / githubWorkflowAddedJobs += WorkflowJob(
  id = "scripted",
  name = "Scripted",
  scalas = List(scala212, scala3),
  javas = List(JavaSpec.temurin("17")),
  matrixAdds = Map("test" -> scriptedTestIds),
  steps = githubWorkflowJobSetup.value.toList :+
    WorkflowStep.Sbt(
      commands = List("scripted ${{ matrix.test }}"),
      name = Some("Run scripted ${{ matrix.test }}"),
    ),
)

lazy val sbtPlugin = project
  .settings(
    name := "smithy-trait-codegen-sbt",
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-trait-codegen" % "1.70.0",
      "software.amazon.smithy" % "smithy-model" % "1.70.0",
      "software.amazon.smithy" % "smithy-syntax" % "1.70.0",
      "com.lihaoyi" %% "os-lib" % "0.11.8",
      "org.scalameta" %% "munit" % "1.3.0" % Test,
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.9.8"
        case _      => "2.0.0-RC12"
      }
    },
    scriptedLaunchOpts :=
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false,
    mimaPreviousArtifacts := Set.empty,
  )
  .enablePlugins(SbtPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(sbtPlugin)
  .enablePlugins(NoPublishPlugin)
