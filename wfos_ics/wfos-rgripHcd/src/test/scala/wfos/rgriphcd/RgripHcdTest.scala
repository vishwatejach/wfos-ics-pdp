package wfos.rgripHcd

import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.CSWService.{LocationServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

import csw.params.core.generics.{Parameter}
import csw.params.commands.{Setup, Observe, CommandName}
import csw.params.commands.CommandResponse._
import csw.params.commands.CommandIssue._
import csw.command.client.CommandServiceFactory
import csw.logging.client.scaladsl.LoggingSystemFactory
import wfos.rgriphcd.RgripInfo

// Since our current code uses only location service and event service we will start only those services.
// This is done By passing in the needed services in the constructor,
// those services are started in the superclass’s(i.e., ScalaTestFrameworkTestKit) beforeAll method
class RgripHcdTest extends ScalaTestFrameworkTestKit(LocationServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one HCD run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("RgriphcdStandalone.conf"))

    LoggingSystemFactory.forTestingOnly()
  }

  test("HCD should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.rgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }

  test("HCD should not accept Observe commands") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.rgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val rgripHcdCS = CommandServiceFactory.make(akkaLocation)

    val command: Observe = Observe(Prefix("wfos.rgriphcd"), CommandName("move"), Some(RgripInfo.obsId))
    val response         = Await.result(rgripHcdCS.submit(command), 5000.millis)

    response.asInstanceOf[Invalid].issue shouldBe a[WrongCommandTypeIssue]
  }

  test("HCD should be able to validate a command and return Invalid type response") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.rgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val rgripHcdCS = CommandServiceFactory.make(akkaLocation)

    val targetAngle: Parameter[Int]    = RgripInfo.targetAngleKey.set(35)
    val gratingMode: Parameter[String] = RgripInfo.gratingModeKey.set("bgid3")
    val cw: Parameter[Int]             = RgripInfo.cwKey.set(6000)

    val command: Setup = Setup(Prefix("wfos.rgriphcd"), CommandName("move"), Some(RgripInfo.obsId)).madd(targetAngle, gratingMode, cw)

    val response = Await.result(rgripHcdCS.submit(command), 5000.millis)
    response.asInstanceOf[Invalid].issue shouldBe a[ParameterValueOutOfRangeIssue]
  }

  test("HCD should be able to execute a command and return Completed response") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.rgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val rgripHcdCS = CommandServiceFactory.make(akkaLocation)

    val targetAngle: Parameter[Int]    = RgripInfo.targetAngleKey.set(30)
    val gratingMode: Parameter[String] = RgripInfo.gratingModeKey.set("bgid3")
    val cw: Parameter[Int]             = RgripInfo.cwKey.set(6000)

    val command: Setup = Setup(Prefix("wfos.rgriphcd"), CommandName("move"), Some(RgripInfo.obsId)).madd(targetAngle, gratingMode, cw)

    val response = Await.result(rgripHcdCS.submit(command), 5000.millis)
    response shouldBe a[Completed]
  }

}
