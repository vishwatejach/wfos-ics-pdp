package wfos.lgriphcd

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.core.models.{Id}
import csw.params.commands.CommandIssue.{ParameterValueOutOfRangeIssue, UnsupportedCommandIssue}
import csw.params.commands.{ControlCommand, CommandName, Observe, Setup}
import csw.params.core.generics.{Parameter, Key, KeyType}
import csw.time.core.models.UTCTime

import scala.concurrent.{ExecutionContextExecutor}
import wfos.bgrxassembly.config.LgripInfo
import csw.params.events.{SystemEvent, EventName}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Lgriphcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class LgriphcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val prefix                        = cswCtx.componentInfo.prefix
  private val publisher                     = eventService.defaultPublisher

  // val rgripHcd = new RgripHcd()

  // Called when the component is created
  override def initialize(): Unit = {
    log.info(s"Initializing $prefix")
    log.info(s"Checking if $prefix is at home position")

    log.info(s"${LgripInfo.homePosition.head}, ${LgripInfo.currentPosition.head}")
    if (LgripInfo.currentPosition.head != LgripInfo.homePosition.head) {
      log.error("LgripHcd : gripper is not at the home position")

      val targetPosition: Parameter[Int] = LgripInfo.targetPositionKey.set(LgripInfo.homePosition.head)
      val sc1: Setup                     = Setup(prefix, CommandName("move"), Some(LgripInfo.obsId)).madd(targetPosition)

      val validateResponse = validateCommand(Id(), sc1)
      validateResponse match {
        case Accepted(runId) => onSubmit(runId, sc1)
        case Invalid(runId, commandissue) => {
          log.error("LgripHcd : Validation Failure")
          // log.info(s"${validateResponse.commandissue}")
          log.error(s"$commandissue")
          Invalid(runId, commandissue)
        }
      }
    }
    else {
      log.info("LgripHcd : Gripper is at exchange position")
    }
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"LgripHcd : Command - $runId is being validated")
    controlCommand match {
      case setup: Setup => {
        val targetPosition = setup(LgripInfo.targetPositionKey)
        if (targetPosition.head != LgripInfo.currentPosition.head) {
          log.info("LgripHcd : Validation Successful")
          Accepted(runId)
        }
        else {
          log.error("LgripHcd : Gripper is already at target angle")
          val stage  = LgripInfo.stageKey.set("Validation")
          val status = LgripInfo.statusKey.set("Failure")
          val event  = SystemEvent(componentInfo.prefix, EventName("LgripHcd_status")).madd(stage, status)
          publisher.publish(event)
          Invalid(runId, ParameterValueOutOfRangeIssue("LgripHcd : Gripper is already at target angle"))
        }
      }
      case _: Observe => Invalid(runId, UnsupportedCommandIssue("LgripHcd accepts only setup commands"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"LgripHcd : handling command: ${controlCommand.commandName} $controlCommand")
    controlCommand match {
      case setup: Setup => onSetup(runId, setup)
      case _            => Invalid(runId, UnsupportedCommandIssue("LgripHcd : Inavlid Command received"))
    }
  }

  private def onSetup(runId: Id, setup: Setup): SubmitResponse = {

    log.info(s"LgripHcd : Executing the received command: $setup")
    val targetPosition: Parameter[Int] = setup(LgripInfo.targetPositionKey)
    val delay: Int                     = 10
    log.info(s"LgripHcd : Gripper is at ${LgripInfo.currentPosition.head}cm")

    if (LgripInfo.currentPosition.head > targetPosition.head) {
      while (LgripInfo.currentPosition.head != targetPosition.head) {
        LgripInfo.currentPosition = LgripInfo.currentPositionKey.set(LgripInfo.currentPosition.head - 1)
        if (LgripInfo.currentPosition.head % 10 == 0) log.info(s"LgripHcd : Moving gripper to ${LgripInfo.currentPosition.head}")
        Thread.sleep(delay)
      }
    }
    else if (LgripInfo.currentPosition.head < targetPosition.head) {
      while (LgripInfo.currentPosition.head != targetPosition.head) {
        LgripInfo.currentPosition = LgripInfo.currentPositionKey.set(LgripInfo.currentPosition.head + 1)
        if (LgripInfo.currentPosition.head % 10 == 0) log.info(s"LgripHcd : Moving gripper to ${LgripInfo.currentPosition.head}")
        Thread.sleep(delay)
      }
    }
    val stage  = LgripInfo.stageKey.set("Execution")
    val status = LgripInfo.statusKey.set("Completed")
    val event  = SystemEvent(componentInfo.prefix, EventName("LgripHcd_status")).madd(stage, status)
    publisher.publish(event)
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
