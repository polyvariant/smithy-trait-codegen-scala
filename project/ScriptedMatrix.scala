/*
 * Copyright 2025 Polyvariant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import org.typelevel.sbt.gha._
import sbt._

/** Build-only helper that produces a discover+run pair of GitHub Actions
  * workflow jobs for an sbt-plugin module's scripted tests, along with a YAML
  * patch that fixes up the `matrixAdds` quoting so the discovered list expands
  * as a real array.
  *
  * sbt-typelevel quotes everything passed via `matrixAdds`, but we need the raw
  * `${{ fromJSON(...) }}` expression to render unquoted. We work around this by
  * emitting a placeholder string and replacing it post-generation. The
  * placeholder is unique per module so multiple matrices can coexist.
  *
  * Tracked upstream: https://github.com/typelevel/sbt-typelevel/issues/887
  */
object ScriptedMatrix {

  private def placeholder(jobIdPrefix: String): String =
    s"PATCH_${jobIdPrefix.replace('-', '_')}_tests_from_needs"

  private def expansion(jobIdPrefix: String): String =
    s"$${{ fromJSON(needs.$jobIdPrefix-discover.outputs.tests) }}"

  /** Build a discover+run pair of [[WorkflowJob]]s for an sbt-plugin module's
    * scripted tests. Must be called from a setting/task macro (it uses the
    * surrounding scope's `ThisBuild / githubWorkflowOSes` and
    * `githubWorkflowJobSetup` settings).
    *
    * Pair the result with a [[patchFor]] call using the same `jobIdPrefix`.
    *
    * @param moduleId
    *   the sbt project id (e.g. "sbtPlugin"). Also used as the on-disk path
    *   prefix `<moduleId>/src/sbt-test`.
    * @param jobIdPrefix
    *   prefix for the two generated job ids: `<prefix>-discover` and `<prefix>`.
    * @param scalas
    *   Scala versions to fan out over.
    * @param javas
    *   JVM versions for the runner.
    * @param oses
    *   OS list (only the first is used for the discover job; the run job uses all).
    * @param jobSetup
    *   the steps to run before the scripted command (typically
    *   `githubWorkflowJobSetup.value.toList`).
    */
  def jobs(
    moduleId: String,
    jobIdPrefix: String,
    scalas: List[String],
    javas: List[JavaSpec],
    oses: List[String],
    jobSetup: List[WorkflowStep],
  ): Seq[WorkflowJob] = {
    val testRoot = s"$moduleId/src/sbt-test"

    val discover = WorkflowJob(
      id = s"$jobIdPrefix-discover",
      name = s"Scripted (discover $moduleId tests)",
      oses = oses.take(1),
      scalas = List.empty,
      javas = List(javas.head),
      sbtStepPreamble = List.empty,
      outputs = Map("tests" -> "steps.list.outputs.tests"),
      steps = List(
        WorkflowStep.CheckoutFull,
        WorkflowStep.Run(
          commands = List(
            s"""tests=$$(find $testRoot -mindepth 2 -maxdepth 2 -type d | sed 's|$testRoot/||' | jq -R . | jq -sc .)""",
            """echo "tests=$tests" >> "$GITHUB_OUTPUT"""",
            """echo "Discovered: $tests"""",
          ),
          id = Some("list"),
          name = Some("List scripted test folders"),
        ),
      ),
    )

    val run = WorkflowJob(
      id = jobIdPrefix,
      name = s"Scripted ($moduleId)",
      needs = List(s"$jobIdPrefix-discover"),
      scalas = scalas,
      javas = javas,
      matrixAdds = Map("test" -> List(placeholder(jobIdPrefix))),
      steps = jobSetup :+
        WorkflowStep.Sbt(
          commands = List(s"$moduleId/scripted $${{ matrix.test }}"),
          name = Some(s"Run scripted $${{ matrix.test }}"),
        ),
    )

    Seq(discover, run)
  }

  /** YAML patch that replaces the placeholder added by [[jobs]] with the
    * `${{ fromJSON(...) }}` expression GHA needs to expand the discovered test
    * list as a real matrix axis. Same `jobIdPrefix` must be used here as in [[jobs]].
    */
  def patchFor(jobIdPrefix: String): String => String =
    _.replace(s"[${placeholder(jobIdPrefix)}]", expansion(jobIdPrefix))

}
