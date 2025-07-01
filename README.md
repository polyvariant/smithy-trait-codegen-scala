# smithy-trait-codegen-scala

Generate Java classes (with builders, serialization to/from `Node`, trait providers) for Smithy traits, for usage in Scala projects.

## Usage

### sbt

In `project/plugins.sbt`:

```scala
addSbtPlugin("org.polyvariant" % "smithy-trait-codegen-sbt" % version)
```

In `build.sbt`:

```scala
// on a Java-only module, e.g. autoScalaLibrary := false, crossPaths := false
.enablePlugins(SmithyTraitCodegenPlugin)
.settings(
  smithyTraitCodegenDependencies := Nil,
  smithyTraitCodegenNamespace := "my.ns",
  smithyTraitCodegenJavaPackage := "my.pkg",
)
```

### mill

Currently not supported in this plugin. See https://github.com/simple-scala-tooling/smithy-trait-codegen-mill/ for an alternative.

## Useful patterns

### Mixing generated and handwritten providers

In case you want to keep some handwritten trait providers, e.g. if you have union traits (which are currently not supported in smithy-trait-codegen), or want to keep binary compatibility on existing traits.

Instead of `META-INF/services/software.amazon.smithy.model.traits.TraitService`, add the providers to

`META-INF/services-input/software.amazon.smithy.model.traits.TraitService`

and add this snippet to include those in the actual, generated file under `META-INF/services`:

```scala
smithyTraitCodegenExternalProviders ++=
  IO.readLines(
    (ThisBuild / baseDirectory).value / "modules" / "protocol" / "resources" / "META-INF" / "services-input" / classOf[TraitService].getName()
  ).filterNot(_.trim.startsWith("#"))
    .filterNot(_.trim.isEmpty),
Compile / packageBin / mappings ~= {
  _.filterNot { case (_, path) => path.contains("services-input") }
}
```

You can also hardcode `smithyTraitCodegenExternalProviders` right there:

```scala
smithyTraitCodegenExternalProviders ++= List("my.pkg.Trait1$Provider", "my.pkg.Trait2$Provider")
```
