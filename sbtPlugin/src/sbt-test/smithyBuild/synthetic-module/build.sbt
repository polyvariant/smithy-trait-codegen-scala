val core = project
  .enablePlugins(SmithyBuildPlugin)
  .settings(
    smithyBuildSettings := loadSmithyBuild().value
  )

val root = project
  .in(file("."))

val checkSyntheticProject = taskKey[Unit](
  "Verify the core-smithyBuild synthetic project carries the expected source roots, deps, and resolvers"
)

checkSyntheticProject := {
  val state = Keys.state.value
  val structure = Project.extract(state).structure
  val ide = structure
    .allProjectRefs
    .find(_.project == "core-smithyBuild")
    .getOrElse(sys.error("core-smithyBuild project was not derived"))

  val coreBase = (LocalProject("core") / baseDirectory)
    .get(structure.data)
    .getOrElse(sys.error("no core base"))

  val srcDirs = (ide / Compile / unmanagedSourceDirectories).get(structure.data).getOrElse(Nil)
  val modelDir = coreBase / "model"
  if (!srcDirs.contains(modelDir))
    sys.error(s"unmanagedSourceDirectories did not contain $modelDir; got: $srcDirs")

  val deps = (ide / libraryDependencies).get(structure.data).getOrElse(Nil)
  if (
    !deps
      .exists(m => m.organization == "software.amazon.smithy" && m.name == "smithy-trait-codegen")
  )
    sys.error(s"libraryDependencies missing smithy-trait-codegen; got: $deps")

  val resolversValue = (ide / resolvers).get(structure.data).getOrElse(Nil)
  if (!resolversValue.exists(_.name == "central"))
    sys.error(s"resolvers missing central; got: $resolversValue")

  val syntheticBase = (ide / baseDirectory).get(structure.data).getOrElse(sys.error("no base"))
  val expectedBase = coreBase / "target" / "core-smithyBuild"
  if (syntheticBase.getCanonicalFile != expectedBase.getCanonicalFile)
    sys.error(s"synthetic project base dir wrong: $syntheticBase vs $expectedBase")
}
