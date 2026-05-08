val root = project
  .in(file("."))
  .enablePlugins(SmithyBuildPlugin)

val checkSyntheticProject = taskKey[Unit](
  "Verify the smithy-build-ide synthetic project carries the expected source roots, deps, and resolvers"
)

checkSyntheticProject := {
  val projects = (LocalRootProject / Keys.thisProjectRef).value.build
  val state = Keys.state.value
  val structure = Project.extract(state).structure
  val ide = structure
    .allProjectRefs
    .find(_.project == "smithy-build-ide")
    .getOrElse(sys.error("smithy-build-ide project was not derived"))

  val srcDirs = (ide / Compile / unmanagedSourceDirectories).get(structure.data).getOrElse(Nil)
  val modelDir = baseDirectory.value / "model"
  if (!srcDirs.contains(modelDir))
    sys.error(s"unmanagedSourceDirectories did not contain $modelDir; got: $srcDirs")

  val deps = (ide / libraryDependencies).get(structure.data).getOrElse(Nil)
  if (!deps.exists(m => m.organization == "software.amazon.smithy" && m.name == "smithy-trait-codegen"))
    sys.error(s"libraryDependencies missing smithy-trait-codegen; got: $deps")

  val resolversValue = (ide / resolvers).get(structure.data).getOrElse(Nil)
  if (!resolversValue.exists(_.name == "central"))
    sys.error(s"resolvers missing central; got: $resolversValue")

  val syntheticBase = (ide / baseDirectory).get(structure.data).getOrElse(sys.error("no base"))
  val expectedBase = baseDirectory.value / "target" / "smithy-build-ide"
  if (syntheticBase.getCanonicalFile != expectedBase.getCanonicalFile)
    sys.error(s"synthetic project base dir wrong: $syntheticBase vs $expectedBase")
}
