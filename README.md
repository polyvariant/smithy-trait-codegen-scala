# smithy-scala-tools

Build-time tooling for working with [Smithy](https://smithy.io) models from Scala projects.

<!-- omit in toc -->
## Table of contents

- [smithy-scala-tools](#smithy-scala-tools)
  - [Installation](#installation)
  - [`SmithyTraitCodegenPlugin`](#smithytraitcodegenplugin)
    - [Usage](#usage)
    - [mill](#mill)
    - [Useful patterns](#useful-patterns)
      - [Mixing generated and handwritten providers](#mixing-generated-and-handwritten-providers)
  - [`SmithyFormatPlugin`](#smithyformatplugin)
    - [Tasks](#tasks)
    - [Configuration](#configuration)
    - [Wiring `smithyFmtCheckAll` into CI](#wiring-smithyfmtcheckall-into-ci)
  - [`SmithyDocGenPlugin`](#smithydocgenplugin)
    - [Usage](#usage-1)
    - [Settings](#settings)
  - [FAQ](#faq)
    - [What's the difference between this and smithy4s?](#whats-the-difference-between-this-and-smithy4s)

## Installation

In `project/plugins.sbt`:

```scala
addSbtPlugin("org.polyvariant" % "smithy-scala-tools-sbt" % version)
```

All plugins live in this artifact — there is nothing else to add.

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

| Task                | Scope                       | Description                                                        |
| ------------------- | --------------------------- | ------------------------------------------------------------------ |
| `smithyFmt`         | `Compile` (default), `Test` | Format `.smithy` files in this configuration's source directories. |
| `smithyFmtCheck`    | `Compile` (default), `Test` | Fail if any `.smithy` file would be reformatted.                   |
| `smithyFmtAll`      | project                     | Run `smithyFmt` for both `Compile` and `Test`.                     |
| `smithyFmtCheckAll` | project                     | Run `smithyFmtCheck` for both `Compile` and `Test`.                |

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

> A simpler one-liner is being tracked upstream: [typelevel/sbt-typelevel#888](https://github.com/typelevel/sbt-typelevel/issues/888).

## `SmithyDocGenPlugin`

Generates API documentation from Smithy models using AWS's [smithy-docgen](https://github.com/smithy-lang/smithy/tree/main/smithy-docgen) plugin. Not auto-enabled — opt in on the modules that need it.

### Usage

In `build.sbt`:

```scala
.enablePlugins(SmithyDocGenPlugin)
.settings(
  smithyDocGenService := "com.example#MyService",
  // optional, defaults to Format.Markdown
  smithyDocGenFormat := SmithyDocGenPlugin.Format.Markdown,
  // optional, defaults to Nil
  smithyDocGenDependencies := Nil,
)
```

For Sphinx-based output, use `Format.SphinxMarkdown(...)`. By default, `autoBuild = false` — `smithy-docgen` will only emit the Sphinx project files (`conf.py`, `requirements.txt`, `Makefile`, `content/`). Set `autoBuild = true` if you want `smithy-docgen` to invoke Sphinx and render the docs, but note this requires Python 3 on `PATH` and network access (the integration creates a venv and pip-installs Sphinx).

By default, the plugin reads `.smithy` files from `src/main/resources/META-INF/smithy` and writes the generated documentation to `target/smithy-docgen-output/`. The `generateSmithyDocs` task runs the generation and returns the output directory.

```sh
sbt generateSmithyDocs
```

### Settings

| Setting                       | Type             | Description                                                                            |
| ----------------------------- | ---------------- | -------------------------------------------------------------------------------------- |
| `smithyDocGenService`         | `String`         | Required. Shape ID of the service to document, e.g. `"com.example#MyService"`.         |
| `smithyDocGenFormat`          | `Format`         | Output format ADT. `Format.Markdown` (default) or `Format.SphinxMarkdown(...)`.        |
| `smithyDocGenDependencies`    | `List[ModuleID]` | Extra Smithy model jars to include in the assembler.                                   |
| `smithyDocGenSourceDirectory` | `File`           | Where to look for `.smithy` files. Defaults to `Compile / resourceDirectory / META-INF / smithy`. |
| `smithyDocGenTargetDirectory` | `File`           | Where to write generated docs. Defaults to `Compile / target`.                         |

## FAQ

### What's the difference between this and smithy4s?

They operate on a different level — they're not alternatives.

[smithy4s](https://disneystreaming.github.io/smithy4s/) turns Smithy models into Scala runtime code: a codegen + runtime library for building and consuming Smithy-defined services across JVM, JS, and Native. Its audience is application developers.

`smithy-scala-tools` is a collection of build-time utilities, each aimed at a different audience:

- **`SmithyTraitCodegenPlugin`** is for **protocol authors** — people defining custom Smithy traits and shipping them as artifacts that downstream consumers (including smithy4s users) load via Smithy's model assembler. It generates the Java glue that makes that work.
- **`SmithyFormatPlugin`** sits **side-by-side with smithy4s**: anyone writing `.smithy` files can use it to keep them formatted, regardless of what (if anything) consumes those models afterwards.
- **`SmithyDocGenPlugin`** is for **anyone publishing a Smithy-modelled API** who wants browsable documentation for it — it runs AWS's `smithy-docgen` to turn the model into markdown.

A project can (and often will) use both this and smithy4s.
