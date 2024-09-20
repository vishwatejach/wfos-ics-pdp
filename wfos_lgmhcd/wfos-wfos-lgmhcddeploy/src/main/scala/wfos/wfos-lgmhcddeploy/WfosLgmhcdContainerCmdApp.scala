package wfos.wfoslgmhcddeploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object WfosLgmhcdContainerCmdApp extends App {

  ContainerCmd.start("wfos_lgmhcd_container_cmd_app", Subsystem.withNameInsensitive("WFOS"), args)

}
