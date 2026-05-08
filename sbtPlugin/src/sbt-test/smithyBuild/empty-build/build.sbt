val root = project
  .in(file("."))
  .enablePlugins(SmithyBuildPlugin)
  .settings(
    smithyBuildSettings := loadSmithyBuild().value,
  )

val checkSyntheticProject = taskKey[Unit](
  "Verify the synthetic project derives an empty SmithyBuild when smithy-build.json is absent"
)

checkSyntheticProject := {
  val state = Keys.state.value
  val structure = Project.extract(state).structure
  val ide = structure
    .allProjectRefs
    .find(_.project == "root-smithyBuild")
    .getOrElse(sys.error("root-smithyBuild project should still be derived"))

  val srcDirs = (ide / Compile / unmanagedSourceDirectories).get(structure.data).getOrElse(Nil)
  if (srcDirs.nonEmpty)
    sys.error(s"expected no source dirs without smithy-build.json; got: $srcDirs")

  val deps = (ide / libraryDependencies).get(structure.data).getOrElse(Nil)
  if (deps.nonEmpty)
    sys.error(s"expected no deps without smithy-build.json; got: $deps")
}
