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

// The test axis is populated at CI time by the `scripted-discover` job.
// sbt-typelevel quotes matrixAdds values, but we need the raw GHA expression
// `${{ fromJSON(...) }}` to render unquoted so it expands to the matrix array.
// The placeholder below is rewritten in `githubWorkflowGenerate` below.
// Tracked upstream: https://github.com/typelevel/sbt-typelevel/issues/887
val scriptedTestPlaceholder = "PATCH_scripted_tests_from_needs"
val scriptedTestExpansion = "${{ fromJSON(needs.scripted-discover.outputs.tests) }}"

ThisBuild / githubWorkflowAddedJobs ++= Seq(
  WorkflowJob(
    id = "scripted-discover",
    name = "Scripted (discover tests)",
    oses = (ThisBuild / githubWorkflowOSes).value.toList.take(1),
    scalas = List.empty,
    javas = List((ThisBuild / githubWorkflowJavaVersions).value.head),
    sbtStepPreamble = List.empty,
    outputs = Map("tests" -> "steps.list.outputs.tests"),
    steps = List(
      WorkflowStep.CheckoutFull,
      WorkflowStep.Run(
        commands = List(
          """tests=$(find sbtPlugin/src/sbt-test -mindepth 2 -maxdepth 2 -type d | sed 's|sbtPlugin/src/sbt-test/||' | jq -R . | jq -sc .)""",
          """echo "tests=$tests" >> "$GITHUB_OUTPUT"""",
          """echo "Discovered: $tests"""",
        ),
        id = Some("list"),
        name = Some("List scripted test folders"),
      ),
    ),
  ),
  WorkflowJob(
    id = "scripted",
    name = "Scripted",
    needs = List("scripted-discover"),
    scalas = List(scala212, scala3),
    javas = List(JavaSpec.temurin("17")),
    matrixAdds = Map("test" -> List(scriptedTestPlaceholder)),
    steps =
      githubWorkflowJobSetup.value.toList :+
        WorkflowStep.Sbt(
          commands = List("scripted ${{ matrix.test }}"),
          name = Some("Run scripted ${{ matrix.test }}"),
        ),
  ),
)

// Upstream `githubWorkflowGenerate` renders all matrix values through `wrap`,
// which quotes the `${{ fromJSON(...) }}` expression we need for the scripted
// matrix axis. We work around this with a placeholder + post-process pass.
// Two tasks below cooperate symmetrically: one regenerates and applies the
// patch; the other regenerates, applies the patch in memory, and compares.
// The CI workflow's "up to date" check calls the patched check task so it
// sees the same form as what's on disk.
// Tracked upstream: https://github.com/typelevel/sbt-typelevel/issues/887
val patchScriptedMatrix: String => String =
  yaml =>
    yaml
      .replace(s"[$scriptedTestPlaceholder]", scriptedTestExpansion)
      // route the in-workflow staleness check through our patched task so it
      // compares apples to apples
      .replace("sbt githubWorkflowCheck", "sbt githubWorkflowCheckWithMatrixPatch")

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

lazy val root = project
  .in(file("."))
  .aggregate(core, sbtPlugin)
  .enablePlugins(NoPublishPlugin)
