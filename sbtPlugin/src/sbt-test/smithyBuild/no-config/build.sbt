val root = project
  .in(file("."))
  .enablePlugins(SmithyBuildPlugin)

val checkNoSyntheticProject = taskKey[Unit](
  "Verify no synthetic project is derived when smithy-build.json is absent"
)

checkNoSyntheticProject := {
  val state = Keys.state.value
  val structure = Project.extract(state).structure
  if (structure.allProjectRefs.exists(_.project == "smithy-build-ide"))
    sys.error("smithy-build-ide project should not exist without smithy-build.json")
}
