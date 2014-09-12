import OcsKeys._

// note: inter-project dependencies are declared at the top, in projects.sbt

name := "edu.gemini.sp.vcs.tui"

// version set in ThisBuild

unmanagedJars in Compile ++= Seq(
  // new File(baseDirectory.value, "../../lib/bundle/org.apache.felix-4.0.3.jar"),
  // new File(baseDirectory.value, "../../lib/bundle/org.apache.felix-4.2.1.jar"),
  new File(baseDirectory.value, "../../lib/bundle/org.osgi.core-4.3.0.jar"),
  // new File(baseDirectory.value, "../../lib/bundle/org.scala-lang.scala-library_2.10.1.v20130302-092018-VFINAL-33e32179fd.jar"),
  new File(baseDirectory.value, "../../lib/bundle/scalaz-core_2.10-7.0.5.jar"),
  new File(baseDirectory.value, "../../lib/bundle/scalaz-effect_2.10-7.0.5.jar"),
  new File(baseDirectory.value, "../../lib/bundle/org-dom4j_2.10-1.5.1.jar")
)

osgiSettings

ocsBundleSettings

OsgiKeys.bundleActivator := Some("edu.gemini.sp.vcs.server.osgi.Activator")

OsgiKeys.privatePackage := Seq(
  "edu.gemini.sp.vcs.server.*",
  "edu.gemini.sp.vcs.tui.*"
)

OsgiKeys.bundleSymbolicName := name.value

OsgiKeys.dynamicImportPackage := Seq("")

OsgiKeys.exportPackage := Seq()

        