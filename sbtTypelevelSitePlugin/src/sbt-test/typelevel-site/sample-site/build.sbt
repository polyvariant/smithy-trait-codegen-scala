import laika.helium.config.HeliumIcon
import laika.helium.config.IconLink

val docs = project
  .in(file("."))
  .enablePlugins(SmithyDocGenPlugin, SmithyDocGenTypelevelSitePlugin)
  .settings(
    smithyDocGenService := "demo#Weather",
    tlSiteIsTypelevelProject := None,
    // Helium needs an explicit home link target. Without a git remote in the
    // scripted-test sandbox we can't auto-derive one, so set it directly.
    tlSiteHelium ~= {
      _.site.topNavigationBar(
        homeLink = IconLink.external("https://example.org/", HeliumIcon.home)
      )
    },
  )
