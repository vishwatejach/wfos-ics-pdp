package wfos.wfoslgmhcddeploy

import csw.framework.deploy.hostconfig.HostConfig
import csw.prefix.models.Subsystem

object WfosLgmhcdHostConfigApp extends App {

  HostConfig.start("wfos_lgmhcd_host_config_app", Subsystem.withNameInsensitive("WFOS"), args)

}
