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
import csw.params.commands.{Setup, Observe, CommandName}
import csw.params.commands.CommandResponse._
import csw.params.commands.CommandIssue._
import csw.logging.client.scaladsl.LoggingSystemFactory
import wfos.rgriphcd.RgripInfo

class BgrxassemblyTest extends ScalaTestFrameworkTestKit(LocationServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one Assembly run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("bgrxassemblyStandalone.conf"))
    LoggingSystemFactory.forTestingOnly()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  test("Assembly should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.bgrxAssembly"), ComponentType.Assembly))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }

  test("Assembly should be able to validate commands and send Invalid Response") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.bgrxAssembly"), ComponentType.Assembly))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val targetAngle: Parameter[Int]    = RgripInfo.targetAngleKey.set(30)
    val gratingMode: Parameter[String] = RgripInfo.gratingModeKey.set("bgid6")
    val cw: Parameter[Int]             = RgripInfo.cwKey.set(6000)

    val command: Setup = Setup(Prefix("wfos.bgrxAssembly"), CommandName("move"), Some(RgripInfo.obsId)).madd(targetAngle, gratingMode, cw)

    val bgrxCS   = CommandServiceFactory.make(akkaLocation)
    val response = Await.result(bgrxCS.submit(command), 5000.millis)

    response.asInstanceOf[Invalid].issue shouldBe a[WrongParameterTypeIssue]
  }
}
