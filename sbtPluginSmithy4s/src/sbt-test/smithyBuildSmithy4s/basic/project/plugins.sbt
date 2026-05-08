sys.props.get("plugin.version") match {
  case Some(x) => addSbtPlugin("org.polyvariant" % "smithy-scala-tools-sbt-smithy4s" % x)
  case _       => sys.error("""|The system property 'plugin.version' is not defined.
                         |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
}

addSbtPlugin("com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "0.19.4")
