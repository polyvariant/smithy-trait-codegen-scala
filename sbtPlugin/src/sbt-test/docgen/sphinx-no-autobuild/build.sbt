val root = project
  .in(file("."))
  .enablePlugins(SmithyDocGenPlugin)
  .settings(
    smithyDocGenService := "demo#Weather",
    smithyDocGenFormat := SmithyDocGenPlugin.Format.SphinxMarkdown(autoBuild = false),
  )
