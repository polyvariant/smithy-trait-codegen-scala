# smithy-trait-codegen-scala

Smithy tooling for sbt projects. This artifact ships two independent sbt plugins:

- **`SmithyTraitCodegenPlugin`** — generate Java classes (with builders, `Node` serialization, trait providers) for Smithy traits, for use from Scala projects. Opt-in per project.
- **`SmithyFormatPlugin`** — format `.smithy` files using [smithy-syntax](https://github.com/smithy-lang/smithy)'s `Formatter`, like `sbt-scalafmt` but for Smithy IDL. Auto-enabled on every project.

Cross-built for sbt 1 (Scala 2.12) and sbt 2 (Scala 3).

## Installation

In `project/plugins.sbt`:

```scala
addSbtPlugin("org.polyvariant" % "smithy-trait-codegen-sbt" % version)
```

Both plugins live in this artifact — there is nothing else to add.

## `SmithyTraitCodegenPlugin`

Generates Java sources and resources from Smithy trait definitions. Not auto-enabled — opt in on the modules that need it.

### Usage

In `build.sbt`:

```scala
// on a Java-only module, e.g. autoScalaLibrary := false, crossPaths := false
.enablePlugins(SmithyTraitCodegenPlugin)
.settings(
  smithyTraitCodegenDependencies := Nil,
  smithyTraitCodegenNamespace := "my.ns",
  // Note: this is the "root" package of generated output.
  // Sub-namespaces of my.ns will be generated as sub-packages of this package, as seen from my.ns.
  // For example, my.ns.foo will be generated as my.pkg.foo.
  smithyTraitCodegenJavaPackage := "my.pkg",
)
```

### mill

Currently not supported in this plugin. See https://github.com/simple-scala-tooling/smithy-trait-codegen-mill/ for an alternative.

### Useful patterns

#### Mixing generated and handwritten providers

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

## `SmithyFormatPlugin`

Formats `.smithy` files in place. Auto-enabled on every project. By default it scans `src/main/smithy` and `src/main/resources/META-INF/smithy` (and their `Test` equivalents) for `.smithy` files.

### Tasks

| Task | Scope | Description |
| --- | --- | --- |
| `smithyFmt` | `Compile` (default), `Test` | Format `.smithy` files in this configuration's source directories. |
| `smithyFmtCheck` | `Compile` (default), `Test` | Fail if any `.smithy` file would be reformatted. |
| `smithyFmtAll` | project | Run `smithyFmt` for both `Compile` and `Test`. |
| `smithyFmtCheckAll` | project | Run `smithyFmtCheck` for both `Compile` and `Test`. |

```sh
sbt smithyFmt           # = Compile/smithyFmt
sbt Test/smithyFmt
sbt smithyFmtAll

sbt smithyFmtCheck      # = Compile/smithyFmtCheck
sbt Test/smithyFmtCheck
sbt smithyFmtCheckAll
```

### Configuration

Override the directories that get scanned:

```scala
Compile / smithyFmtSourceDirectories += baseDirectory.value / "smithy"
```

### Wiring `smithyFmtCheckAll` into CI

Both `sbt-typelevel` and `sbt-github-actions` (the [`com.github.sbt`](https://github.com/sbt/sbt-github-actions) variant) generate the workflow from `githubWorkflowBuild`. The lint step they emit is named `"Check headers and formatting"` — fold the Smithy check into it so it runs alongside scalafmt/header checks:

```scala
ThisBuild / githubWorkflowBuild ~= {
  _.map {
    case step: WorkflowStep.Sbt if step.name == Some("Check headers and formatting") =>
      step.withCommands(step.commands :+ "smithyFmtCheckAll")
    case other => other
  }
}
```

Then regenerate the workflow:

```sh
sbt githubWorkflowGenerate
```
