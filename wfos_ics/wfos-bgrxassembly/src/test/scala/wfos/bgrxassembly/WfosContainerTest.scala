package wfos.bgrxassembly

//import akka.actor.Status.Success
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.prefix.models.Prefix
import csw.location.api.models.{ComponentId, ComponentType}

import csw.params.commands.Setup
//import csw.prefix.models.Subsystem.WFOS
import csw.testkit.scaladsl.CSWService.{LocationServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

import csw.params.core.generics.{Parameter}
import csw.params.commands.{Observe, CommandName}
import csw.params.commands.CommandResponse._
import csw.params.commands.CommandIssue._
import csw.logging.client.scaladsl.LoggingSystemFactory
import wfos.rgriphcd.RgripInfo

class WfosContainerTest extends ScalaTestFrameworkTestKit(LocationServer, EventServer) with AnyFunSuiteLike {
  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one Assembly run for all tests
    spawnContainer(com.typesafe.config.ConfigFactory.load("wfosContainer.conf"))
    LoggingSystemFactory.forTestingOnly()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  test("All components in the container should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.bgrxAssembly"), ComponentType.Assembly))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val rConnection   = AkkaConnection(ComponentId(Prefix("wfos.rgriphcd"), ComponentType.HCD))
    val rAkkaLocation = Await.result(locationService.resolve(rConnection, 10.seconds), 10.seconds).get

    val lConnection   = AkkaConnection(ComponentId(Prefix("wfos.lgriphcd"), ComponentType.HCD))
    val lAkkaLocation = Await.result(locationService.resolve(lConnection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
    rAkkaLocation.connection shouldBe rConnection
    lAkkaLocation.connection shouldBe lConnection
  }

  test("Assembly should be able to accept Setup commands of move type and execute them successfully") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.bgrxAssembly"), ComponentType.Assembly))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val targetAngle: Parameter[Int]    = RgripInfo.targetAngleKey.set(30)
    val gratingMode: Parameter[String] = RgripInfo.gratingModeKey.set("bgid3")
    val cw: Parameter[Int]             = RgripInfo.cwKey.set(6000)

    val bgrxCS         = CommandServiceFactory.make(akkaLocation)
    val command: Setup = Setup(Prefix("wfos.bgrxAssembly"), CommandName("move"), Some(RgripInfo.obsId)).madd(targetAngle, gratingMode, cw)

    // val response = bgrxCS.submit(command)
    // Thread.sleep(5000)
    // response shouldBe a[Started]

    val response = Await.result(bgrxCS.submit(command), 5000.millis)
    response shouldBe a[Started]
  }
}
