
lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `wfos-bgrxassembly`,
  `wfos-lgripHcd`,
  `wfos-rgripHcd`,
  `wfos-wfos-icsdeploy`
)

lazy val `wfos-ics-root` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

// assembly module
lazy val `wfos-bgrxassembly` = project
  .settings(
    libraryDependencies ++= Dependencies.bgrxassembly
  )

// hcd module
lazy val `wfos-lgripHcd` = project
  .dependsOn(
    `wfos-bgrxassembly`
  )
  .settings(
    libraryDependencies ++= Dependencies.lgripHcd
  )

  // hcd module
lazy val `wfos-rgripHcd` = project
  .dependsOn(
    `wfos-bgrxassembly`
  )
  .settings(
    libraryDependencies ++= Dependencies.rgripHcd
  )

// deploy module
lazy val `wfos-wfos-icsdeploy` = project
  .dependsOn(
    `wfos-bgrxassembly`,
    `wfos-lgripHcd`,
    `wfos-rgripHcd`
  )
  .settings(
    libraryDependencies ++= Dependencies.WfosIcsDeploy
  )
