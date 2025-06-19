val traits = project
  .enablePlugins(SmithyTraitCodegenPlugin)
  .settings(
    smithyTraitCodegenDependencies := List(
      "io.github.disneystreaming.alloy" % "alloy-core" % "0.3.23"
    ),
    smithyTraitCodegenJavaPackage := "smithy4bazinga",
    smithyTraitCodegenNamespace := "smithy4bazinga",
  )

val root = project
  .in(file("."))
  .dependsOn(traits)
