ThisBuild / tlBaseVersion := "0.3"
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

// Each (jobs, patch) pair below is the full discover+run matrix for one
// sbt-plugin module's scripted tests. sbt-typelevel-site is sbt 1.x only, so
// its matrix runs on Scala 2.12 only.
//
// The placeholder/patch trick works around an upstream bug where matrixAdds
// values are always quoted but we need the `${{ fromJSON(...) }}` expression
// rendered unquoted. Tracked at https://github.com/typelevel/sbt-typelevel/issues/887
ThisBuild / githubWorkflowAddedJobs ++= {
  val oses = (ThisBuild / githubWorkflowOSes).value.toList
  val setup = githubWorkflowJobSetup.value.toList
  ScriptedMatrix.jobs(
    moduleId = "sbtPlugin",
    jobIdPrefix = "scripted",
    scalas = List(scala212, scala3),
    javas = List(JavaSpec.temurin("17")),
    oses = oses,
    jobSetup = setup,
  ) ++
    ScriptedMatrix.jobs(
      moduleId = "sbtTypelevelSitePlugin",
      jobIdPrefix = "scripted-site",
      scalas = List(scala212),
      javas = List(JavaSpec.temurin("17")),
      oses = oses,
      jobSetup = setup,
    )
}

// Upstream `githubWorkflowGenerate` renders all matrix values through `wrap`,
// which quotes the `${{ fromJSON(...) }}` expression we need for the scripted
// matrix axis. We work around this with a placeholder + post-process pass.
// Two tasks below cooperate symmetrically: one regenerates and applies the
// patch; the other regenerates, applies the patch in memory, and compares.
// The CI workflow's "up to date" check calls the patched check task so it
// sees the same form as what's on disk.
// Tracked upstream: https://github.com/typelevel/sbt-typelevel/issues/887
val patchScriptedMatrix: String => String =
  ScriptedMatrix
    .patchFor("scripted")
    .andThen(ScriptedMatrix.patchFor("scripted-site"))
    // route the in-workflow staleness check through our patched task so it
    // compares apples to apples
    .andThen(_.replace("sbt githubWorkflowCheck", "sbt githubWorkflowCheckWithMatrixPatch"))

val githubWorkflowGenerateWithMatrixPatch = taskKey[Unit](
  "Generate ci.yml via sbt-typelevel, then patch the scripted matrix axis"
)

val githubWorkflowCheckWithMatrixPatch = taskKey[Unit](
  "Check ci.yml is up to date, accounting for the scripted matrix patch"
)

ThisBuild / githubWorkflowGenerateWithMatrixPatch := {
  (LocalRootProject / githubWorkflowGenerate).value
  val ciYml = (LocalRootProject / baseDirectory).value / ".github" / "workflows" / "ci.yml"
  IO.write(ciYml, patchScriptedMatrix(IO.read(ciYml)))
}

// Snapshot the on-disk content to a sibling file BEFORE upstream generation
// runs. Sequencing is done via `Def.sequential` so the snapshot task executes
// before generate (which overwrites the file).
val snapshotCiYml = taskKey[File](
  "Copy ci.yml to a sibling file so it can be diffed after regeneration"
)

ThisBuild / snapshotCiYml := {
  val ciYml = (LocalRootProject / baseDirectory).value / ".github" / "workflows" / "ci.yml"
  val snap = ciYml.getParentFile / s"${ciYml.getName}.snapshot"
  IO.copyFile(ciYml, snap)
  snap
}

ThisBuild / githubWorkflowCheckWithMatrixPatch := Def
  .sequential(
    ThisBuild / snapshotCiYml,
    LocalRootProject / githubWorkflowGenerate,
    Def.task {
      val ciYml = (LocalRootProject / baseDirectory).value / ".github" / "workflows" / "ci.yml"
      val snap = ciYml.getParentFile / s"${ciYml.getName}.snapshot"
      val onDisk = IO.read(snap)
      val regeneratedAndPatched = patchScriptedMatrix(IO.read(ciYml))
      IO.write(ciYml, onDisk)
      IO.delete(snap)
      if (regeneratedAndPatched != onDisk) {
        val tmp = IO.createTemporaryDirectory
        val expected = tmp / "expected.yml"
        val actual = tmp / "actual.yml"
        IO.write(expected, regeneratedAndPatched)
        IO.write(actual, onDisk)
        val out = new java.io.ByteArrayOutputStream
        scala
          .sys
          .process
          .Process(Seq("diff", "-u", actual.getAbsolutePath, expected.getAbsolutePath))
          .#>(out)
          .!
        println(s"diff (actual vs expected):\n${out.toString}")
        sys.error(
          "ci.yml is out of date — run githubWorkflowGenerateWithMatrixPatch"
        )
      }
    },
  )
  .value

// Reroute the `prePR` and `tlPrePrBotHook` aliases (defined by sbt-typelevel)
// to use our patched generate task, so contributors running `sbt prePR` get
// the same ci.yml form that `githubWorkflowCheckWithMatrixPatch` validates.
GlobalScope / tlCommandAliases := (GlobalScope / tlCommandAliases).value.map {
  case (alias, commands) =>
    alias -> commands.map {
      case "githubWorkflowGenerate" => "githubWorkflowGenerateWithMatrixPatch"
      case other                    => other
    }
}

lazy val core = project
  .settings(
    name := "smithy-scala-tools-core",
    libraryDependencies ++= Seq(
      "software.amazon.smithy" % "smithy-trait-codegen" % "1.70.0",
      "software.amazon.smithy" % "smithy-model" % "1.70.0",
      "software.amazon.smithy" % "smithy-syntax" % "1.70.0",
      "software.amazon.smithy" % "smithy-docgen" % "1.70.0",
      "com.lihaoyi" %% "os-lib" % "0.11.8",
      "org.scalameta" %% "munit" % "1.3.0" % Test,
    ),
    mimaPreviousArtifacts := Set.empty,
  )

lazy val sbtPlugin = project
  .dependsOn(core)
  .settings(
    name := "smithy-scala-tools-sbt",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.3.0" % Test
    ),
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

// sbt-typelevel-site is currently sbt 1.x only; sbt 2 work is tracked at
// https://github.com/typelevel/sbt-typelevel/pull/872
lazy val sbtTypelevelSitePlugin = project
  .in(file("sbtTypelevelSitePlugin"))
  .dependsOn(sbtPlugin)
  .settings(
    name := "smithy-scala-tools-sbt-typelevel-site",
    crossScalaVersions := Seq(scala212),
    pluginCrossBuild / sbtVersion := "1.9.8",
    addSbtPlugin("org.typelevel" % "sbt-typelevel-site" % "0.8.5"),
    scriptedLaunchOpts :=
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false,
    mimaPreviousArtifacts := Set.empty,
  )
  .enablePlugins(SbtPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(core, sbtPlugin, sbtTypelevelSitePlugin)
  .enablePlugins(NoPublishPlugin)
