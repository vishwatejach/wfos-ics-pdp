package wfos.bgrxassembly

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.params.commands.CommandResponse._
import csw.params.commands.{ControlCommand, CommandName, Setup, Observe}
import csw.params.commands.CommandIssue.{UnsupportedCommandIssue}
import csw.time.core.models.UTCTime
import csw.params.core.models.{Id, ObsId}

import csw.location.api.models.{AkkaLocation, ComponentId, ComponentType, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.api.models.Connection.AkkaConnection
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.prefix.models.{Prefix, Subsystem}
import csw.params.core.generics.{Key, KeyType, Parameter}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Success, Failure}

import wfos.bgrxassembly.config.{RgripInfo, LgripInfo}
import wfos.bgrxassembly.components.{RgripHcd, LgripHcd}

// import scala.concurrent.duration._

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Rgriphcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */
class BgrxassemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  // #resolve-hcd-and-create-commandservice
  private implicit val system: ActorSystem[Nothing] = ctx.system
  // #resolve-hcd-and-create-commandservice

  private val hcdConnection = AkkaConnection(ComponentId(Prefix(Subsystem.WFOS, "rgripHcd"), ComponentType.HCD))
  // private var hcdLocation: AkkaLocation     = _
  private var rgripHcdCS: Option[CommandService] = None
  private var lgripHcdCS: Option[CommandService] = None

  private val rgripHcd: RgripHcd = new RgripHcd()
  private val lgripHcd: LgripHcd = new LgripHcd()

  // keys of Hcd names
  private val hcdNameKey: Key[String] = KeyType.StringKey.make("hcdName")

  // Prefix of assembly
  val sourcePrefix: Prefix = Prefix("wfos.bgrxassembly")
  private val obsId: ObsId = ObsId("2023A-001-123")

  override def initialize(): Unit = {
    log.info("Initializing bgrxAssembly")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.info("Bgrx Assembly : Locations of components in the assembly are updated")
    log.info(s"Bgrx Assembly : onLocationTrackingEvent is called")
    trackingEvent match {
      case LocationUpdated(location) => {

        location.connection match {
          case AkkaConnection(ComponentId(Prefix(Subsystem.WFOS, "rgriphcd"), _)) => {
            log.info("Bgrx Assembly : Creating command service to RgripHcd")
            rgripHcdCS = Some(CommandServiceFactory.make(location))
          }
          case AkkaConnection(ComponentId(Prefix(Subsystem.WFOS, "lgriphcd"), _)) => {
            log.info("Bgrx Assembly : Creating command service to LgripHcd")
            lgripHcdCS = Some(CommandServiceFactory.make(location))
          }
          case _ => log.info("Unknown HCD encountered")
        }
      }
      case LocationRemoved(connection) => log.info("Location Removed")
    }
    if (rgripHcdCS != None && lgripHcdCS != None) {
      log.info("Bgrx Assembly : All HCDs are successfully initialized")
      sendCommand(Id("bgrx"))
    }
  }

  private def sendCommand(runId: Id): SubmitResponse = {
    val targetAngle: Parameter[Int]    = RgripInfo.targetAngleKey.set(30)
    val gratingMode: Parameter[String] = RgripInfo.gratingModeKey.set("bgid3")
    val cw: Parameter[Int]             = RgripInfo.cwKey.set(6000)
    var hcdName: Parameter[String]     = hcdNameKey.set("wfos.rgriphcd")
    val sc1: Setup                     = Setup(sourcePrefix, CommandName("move"), Some(obsId)).madd(hcdName, targetAngle, gratingMode, cw)

    val validateResponse = validateCommand(runId, sc1)
    validateResponse match {
      case Accepted(runId)       => onSubmit(runId, sc1)
      case Invalid(runId, error) => Invalid(runId, UnsupportedCommandIssue(error.reason))
    }
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(s"Bgrx Assembly : Command - $runId is being validated")

    controlCommand match {
      case setup: Setup =>
        log.info("Bgrx Assembly : Command type validation is Successful")
        log.info("Bgrx Assembly : Validating command name")

        setup.commandName match {
          case CommandName("move") => {

            log.info("Bgrx Assembly : Command name validation is successful")
            log.info("Bgrx Assembly : Validating Parameters")

            log.info(s"${setup.paramSet}")
            val validateParamasRes = rgripHcd.validateParameters(setup)
            validateParamasRes match {

              case Right(_) => {
                log.info("Bgrx Assembly : Parameters' validation is Successful")
                Accepted(runId)
              }
              case Left(error) => {
                log.error(s"RgrpHcd: Validation is Failure. ${error}")
                Invalid(runId, UnsupportedCommandIssue("sda"))
              }
            }
          }
          case _ => {
            log.error(s"Bgrx Assembly : Validation is Failure. $sourcePrefix takes only 'move' Setup as commands")
            Invalid(runId, UnsupportedCommandIssue(s"$sourcePrefix takes only 'move' Setup as commands"))
          }
        }
      case _: Observe =>
        log.error(s"Bgrx Assembly : Validation is Failure. $sourcePrefix prefix only accepts Setup Commands")
        Invalid(runId, UnsupportedCommandIssue("Observe commands are not supported"))
    }
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"Bgrx Assembly : handling command: $runId")
    controlCommand match {
      case setup: Setup => {
        // 1) check if hcd is at home position or not

      }
      case _: Observe => Invalid(runId, UnsupportedCommandIssue("This assembly can't handle observe commands"))
    }

    rgripHcdCS match {
      case Some(cs) => {
        val submitResponseFuture: Future[SubmitResponse] = cs.submit(controlCommand)

        // Handle the completion of the future
        submitResponseFuture.onComplete {
          case Success(response) => {
            log.info(s"Bgrx Assembly : Command submitted successfully: $response")

            // Handle the different response types
            response match {
              case Completed(_, result) => {
                // Perform actions for a completed command
                // You can log, update state, or send other messages as needed
                log.info(s"Bgrx Assembly : Command is executed successfully")
                Completed(runId)
              }
              case Invalid(_, issue) => {
                // Handle the invalid command issue
                // You can log, handle errors, or take other actions
                log.error(s"Bgrx Assembly : Command execution failed with error: $issue")
                Invalid(runId, issue)
              }
              case _ => {
                // Handle other response types if necessary
                Completed(runId)
              }
            }
          }
          case Failure(ex) => {
            log.error(s"Error submitting command: ${ex.getMessage}")
            // Handle the error case if needed
            Invalid(runId, UnsupportedCommandIssue(" "))
          }
        }

        Started(runId) // Return a Started response immediately
      }
      case None => {
        log.error("No HCD available to send the command")
        Error(runId, s"A needed HCD is not available: ${hcdConnection.componentId}")
      }
    }
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
