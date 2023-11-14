package wfos.rgriphcd

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.core.models.{Id}
import csw.params.commands.CommandIssue.{ParameterValueOutOfRangeIssue, UnsupportedCommandIssue, WrongCommandTypeIssue}
import csw.params.commands.{ControlCommand, CommandName, Observe, Setup}

import csw.params.core.generics.{Parameter}
import csw.time.core.models.UTCTime

import scala.concurrent.{ExecutionContextExecutor}
import wfos.bgrxassembly.config.RgripInfo

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Rgriphcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class RgriphcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val prefix                        = cswCtx.componentInfo.prefix

  // Called when the component is created
  override def initialize(): Unit = {
    log.info(s"Initializing $prefix")
    log.info(s"RgripHcd : Checking if $prefix is at home position")

    // val ik: Key[Int]            = KeyType.IntKey.make("IKey")
    // val ikValue: Parameter[Int] = ik.set(1)
    // log.info(s"IK value : ${ikValue.value(0)}")

    log.info(s"RgripHcd : Home Position - ${RgripInfo.exchangeAngle.values.head}, Current Position - ${RgripInfo.currentAngle.values.head}")
    if (RgripInfo.currentAngle.values.head != RgripInfo.homeAngle.values.head) {
      log.error("RgripHcd : gripper is not at the exchange position")

      val targetAngle: Parameter[Int]    = RgripInfo.targetAngleKey.set(RgripInfo.homeAngle.head)
      val gratingMode: Parameter[String] = RgripInfo.gratingModeKey.set("bgid3")
      val cw: Parameter[Int]             = RgripInfo.cwKey.set(6000)
      val sc1: Setup                     = Setup(prefix, CommandName("move"), Some(RgripInfo.obsId)).madd(targetAngle, gratingMode, cw)

      val validateResponse = validateCommand(Id(), sc1)
      validateResponse match {
        case Accepted(runId) => onSubmit(runId, sc1)
        case Invalid(runId, commandissue) => {
          log.error("RgripHcd : Validation Failure")
          // log.info(s"${validateResponse.commandissue}")
          log.error(s"$commandissue")
          Invalid(runId, commandissue)
        }
      }
    }
    else {
      log.info("RgripHcd : Gripper is already at home position")
    }
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"RgripHcd: Command($runId) is being validated")
    controlCommand match {
      case setup: Setup => {
        val targetAngle = setup(RgripInfo.targetAngleKey)
        if (targetAngle.head != RgripInfo.currentAngle.head) {
          Accepted(runId)
        }
        else {
          log.error(s"RgripHcd: Gripper is already at target position")
          Invalid(runId, ParameterValueOutOfRangeIssue("RgripHcd: Gripper is already at target angle"))
        }
      }
      case _: Observe => Invalid(runId, WrongCommandTypeIssue("RgripHcd accepts only setup commands"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"RgripHcd: handling command: runid - $runId")
    controlCommand match {
      case setup: Setup => onSetup(runId, setup)
      case _            => Invalid(runId, UnsupportedCommandIssue("RgripHcd: Inavlid Command received"))
    }
  }

  private def onSetup(runId: Id, setup: Setup): SubmitResponse = {

    log.info(s"RgripHcd: Executing the received command: runid - $runId")
    val targetAngle: Parameter[Int] = setup(RgripInfo.targetAngleKey)
    val delay: Int                  = 500
    log.info(s"RgripHcd: Gripper is at ${RgripInfo.currentAngle.head} degrees")

    // Started(runId)

    if (RgripInfo.currentAngle.head > targetAngle.head) {
      while (RgripInfo.currentAngle.head != targetAngle.head) {
        RgripInfo.currentAngle = RgripInfo.currentAngleKey.set(RgripInfo.currentAngle.head - 1)
        log.info(s"RgripHcd: Rotating gripper to ${RgripInfo.currentAngle.head}")
        Thread.sleep(delay)
      }
    }
    else if (RgripInfo.currentAngle.head < targetAngle.head) {
      while (RgripInfo.currentAngle.head != targetAngle.head) {
        RgripInfo.currentAngle = RgripInfo.currentAngleKey.set(RgripInfo.currentAngle.head + 1)
        log.info(s"RgripHcd: Rotating gripper to ${RgripInfo.currentAngle.head}")
        Thread.sleep(delay)
      }
    }
    Completed(runId)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {
    log.info("shutting Down")
  }

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}
}
