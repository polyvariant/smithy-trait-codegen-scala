val traits = project
  .enablePlugins(SmithyTraitCodegenPlugin)
  .settings(
    smithyTraitCodegenDependencies := List(
      "io.github.disneystreaming.alloy" % "alloy-core" % "0.3.23"
    ),
    // Prior to smithy 1.61.0, namespaces starting with "smithy" were forbidden and required a workaround
    smithyTraitCodegenJavaPackage := "smithy4bazinga",
    smithyTraitCodegenNamespace := "smithy4bazinga",
  )

val root = project
  .in(file("."))
  .dependsOn(traits)
