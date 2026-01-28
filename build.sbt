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

ThisBuild / scalaVersion := "2.12.20"
ThisBuild / tlJdkRelease := Some(8)
ThisBuild / tlFatalWarnings := false

ThisBuild / mergifyStewardConfig ~= (_.map(_.withMergeMinors(true)))

ThisBuild / githubWorkflowBuild ~= {
  _.flatMap {
    case step if step.name == Some("Test") =>
      step ::
        WorkflowStep.Sbt(commands = List("scripted"), id = Some("scripted-test")) ::
        Nil
    case other => other :: Nil
  }
}

val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %%% "weaver-cats" % "0.9.3" % Test
  )
)

lazy val sbtPlugin = project
  .settings(
    name := "smithy-trait-codegen-sbt",
    commonSettings,
    scalaVersion := "2.12.20",
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-trait-codegen" % "1.66.0",
      "software.amazon.smithy" % "smithy-model" % "1.66.0",
    ) ++ Seq(
      "com.lihaoyi" %% "os-lib" % "0.11.7"
    ),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.9.8"
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
