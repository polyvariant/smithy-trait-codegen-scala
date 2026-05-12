import smithy4s.codegen.Smithy4sCodegenPlugin.autoImport.Smithy4s

val core = project
  .enablePlugins(SmithyBuildPlugin, smithy4s.codegen.Smithy4sCodegenPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.disneystreaming.alloy" % "alloy-core" % "0.3.23" % Smithy4s
    )
  )

val root = project
  .in(file("."))

val checkBridge = taskKey[Unit](
  "Verify SmithyBuildSmithy4sPlugin populates smithyBuildSettings from smithy4s keys"
)

checkBridge := {
  val state = Keys.state.value
  val structure = Project.extract(state).structure
  val ide = structure
    .allProjectRefs
    .find(_.project == "core-smithyBuild")
    .getOrElse(sys.error("core-smithyBuild project was not derived"))

  val coreBase =
    (LocalProject("core") / baseDirectory).get(structure.data).getOrElse(sys.error("no core base"))

  val srcDirs = (ide / Compile / unmanagedSourceDirectories).get(structure.data).getOrElse(Nil)
  val expectedSrc = coreBase / "src" / "main" / "smithy"
  if (!srcDirs.contains(expectedSrc))
    sys.error(s"unmanagedSourceDirectories did not contain $expectedSrc; got: $srcDirs")

  val deps = (ide / libraryDependencies).get(structure.data).getOrElse(Nil)
  if (!deps.exists(m => m.organization == "com.disneystreaming.alloy" && m.name == "alloy-core"))
    sys.error(s"libraryDependencies missing alloy-core; got: $deps")
}
