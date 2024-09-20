
lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `wfos-lgmhcdassembly`,
  `wfos-lgmhcd`,
  `wfos-wfos-lgmhcddeploy`
)

lazy val `wfos-lgmhcd-root` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

// assembly module
lazy val `wfos-lgmhcdassembly` = project
  .settings(
    libraryDependencies ++= Dependencies.Lgmhcdassembly
  )

// hcd module
lazy val `wfos-lgmhcd` = project
  .settings(
    libraryDependencies ++= Dependencies.Lgmhcd
  )

// deploy module
lazy val `wfos-wfos-lgmhcddeploy` = project
  .dependsOn(
    `wfos-lgmhcdassembly`,
    `wfos-lgmhcd`
  )
  .enablePlugins(CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.WfosLgmhcdDeploy
  )
