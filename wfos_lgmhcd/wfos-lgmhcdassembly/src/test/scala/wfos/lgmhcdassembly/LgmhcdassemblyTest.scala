package wfos.lgmhcdassembly

import csw.location.api.models.Connection.AkkaConnection
import csw.prefix.models.Prefix
import csw.location.api.models.{ComponentId, ComponentType}
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

class LgmhcdassemblyTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one Assembly run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("LgmhcdassemblyStandalone.conf"))
  }

  test("Assembly should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix("WFOS.lgmHcdAssembly"), ComponentType.Assembly))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }
}
