package wfos.lgriphcd

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
import wfos.bgrxassembly.config.LgripInfo

class LgripHcdTest extends ScalaTestFrameworkTestKit(LocationServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    // uncomment if you want one HCD run for all tests
    spawnStandalone(com.typesafe.config.ConfigFactory.load("LgripHcdStandalone.conf"))
    LoggingSystemFactory.forTestingOnly()
  }

  override def afterAll(): Unit = {
    super.afterAll()
  }

  test("HCD should be locatable using Location Service") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.lgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    akkaLocation.connection shouldBe connection
  }

  test("HCD should not accept Observe commands") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.lgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val lgripHcdCS = CommandServiceFactory.make(akkaLocation)

    val command: Observe = Observe(Prefix("wfos.lgriphcd"), CommandName("move"), Some(LgripInfo.obsId))
    val response         = Await.result(lgripHcdCS.submit(command), 5000.millis)

    response.asInstanceOf[Invalid].issue shouldBe a[UnsupportedCommandIssue]
  }

  test("HCD should be able to validate a command and return Invalid type response") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.lgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val lgripHcdCS = CommandServiceFactory.make(akkaLocation)

    val targetPosition: Parameter[Int] = LgripInfo.targetPositionKey.set(0)
    val command: Setup                 = Setup(Prefix("wfos.lgriphcd"), CommandName("move"), Some(LgripInfo.obsId)).madd(targetPosition)

    val response = Await.result(lgripHcdCS.submit(command), 5000.millis)
    response.asInstanceOf[Invalid].issue shouldBe a[ParameterValueOutOfRangeIssue]
  }

  test("HCD should be able to execute a command and return Completed response") {
    val connection   = AkkaConnection(ComponentId(Prefix("wfos.lgriphcd"), ComponentType.HCD))
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    val lgripHcdCS = CommandServiceFactory.make(akkaLocation)

    val targetPosition = LgripInfo.targetPositionKey.set(50)
    val command: Setup = Setup(Prefix("wfos.lgriphcd"), CommandName("move"), Some(LgripInfo.obsId)).madd(targetPosition)

    val response = Await.result(lgripHcdCS.submit(command), 5000.millis)
    response shouldBe a[Completed]
  }

}
