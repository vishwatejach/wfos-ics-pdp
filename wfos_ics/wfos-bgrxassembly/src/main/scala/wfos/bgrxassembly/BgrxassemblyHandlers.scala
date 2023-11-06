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

import csw.location.api.models.{ComponentId, ComponentType, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.api.models.Connection.AkkaConnection
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.prefix.models.{Prefix, Subsystem}
import csw.params.core.generics.Parameter
// import csw.params.core.generics.{Key,KeyType, Parameter}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Success, Failure}

import wfos.bgrxassembly.config.{RgripInfo, LgripInfo}
import wfos.bgrxassembly.components.{RgripHcd, LgripHcd}
import csw.params.events.{EventKey, EventName}

import akka.util.Timeout
import scala.concurrent.duration._
// import scala.async.Async.{async, await}

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
  // private val lgripHcd: LgripHcd = new LgripHcd()

  implicit val timeout: Timeout = Timeout(5.seconds)

  // keys of Hcd names
  // private val hcdNameKey: Key[String] = KeyType.StringKey.make("hcdName")

  // Prefix of assembly
  val sourcePrefix: Prefix = Prefix("wfos.bgrxassembly")
  private val obsId: ObsId = ObsId("2023A-001-123")

  override def initialize(): Unit = {
    log.info("Initializing BgrxAssembly")
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

    val setup1: Setup = Setup(sourcePrefix, CommandName("move"), Some(obsId)).madd(targetAngle, gratingMode, cw)

    val validateResponse = validateCommand(runId, setup1)
    validateResponse match {
      case Accepted(runId) => {
        val submitResponse = onSubmit(runId, setup1)
        log.info(s"HELLO $submitResponse")
        Started(runId)
      }
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
      // case setup: Setup => onSetup(runId, setup)
      case setup: Setup => {
        val res        = moveRgripHcd(runId, setup)
        val subscriber = eventService.defaultSubscriber

        subscriber.subscribeCallback(
          Set(EventKey(Prefix(Subsystem.WFOS, "lgriphcd"), EventName("LgripHcd_status"))),
          event => {
            val params: Seq[Parameter[String]] = event.paramSet.toSeq.collect { case param: Parameter[String] =>
              param
            }
            val stage: String  = params(0).head
            val status: String = params(1).head

            log.info(s"$stage,$status")
            stage match {
              case "Execution" => {
                status match {
                  case "Completed" => {
                    log.info(s"Bgrx Assembly : Command $runId is executed successfully")
                    // Completed(runId)
                  }
                  case "Failure" => {
                    log.info(s"Bgrx Assembly : Command $runId execution failure")
                    // Invalid(runId, UnsupportedCommandIssue("Failed"))
                  }
                }
              }
              case "Validation" => {
                status match {
                  case "Failure" => {
                    log.info(s"Bgrx Assembly: Command $runId validation failure")
                    log.info(s"Bgrx Assembly: Stopping execution of command $runId")
                    // Invalid(runId, UnsupportedCommandIssue("Validation failure"))
                  }
                  case _ => log.info(s"Bgrx Assembly: Command $runId execution completed")
                }
              }
            }
          }
        )
        Started(runId) // update the started response with completed response
      }
      case _: Observe => Invalid(runId, UnsupportedCommandIssue("This assembly can't handle observe commands"))
    }
  }

  // private def onSetup(runId: Id, setup: Setup): SubmitResponse = {
  //   val targetPosition = LgripInfo.targetPositionKey.set(100)
  //   val command: Setup = Setup(sourcePrefix, CommandName("move"), Some(obsId)).madd(targetPosition)
  //   rgripHcdCS match {
  //     case Some(cs1) => {
  //       val response1: Future[SubmitResponse] = cs1.submit(setup)
  //       response1.onComplete {
  //         case Success(result) => {
  //           result match {
  //             case Completed(_, _) => {
  //               // Perform actions for a completed command
  //               // You can log, update state, or send other messages as needed
  //               log.info(s"Bgrx Assembly: Command $runId is executed successfully")
  //               log.info(s"Bgrx Assembly: RgipHcd has moved to exchange position")
  //               log.info(s"Bgrx Assembly: MOving Lgriphcd to exchange position")
  //               lgripHcdCS match {
  //                 case Some(cs2) => {
  //                   val response2: Future[SubmitResponse] = cs2.submit(command)
  //                   response2.onComplete {
  //                     case Success(result2) => {
  //                       result2 match {
  //                         case Completed(_, _) => {
  //                           log.info(s"Bgrx Assembly: Command $runId is executed successfully")
  //                           log.info(s"Bgrx Assembly: Lgriphcd has moved to exchange position")
  //                         }
  //                         case Invalid(_, issue) => log.error(s"Bgrx Assembly: Command $runId execution failed with error: $issue")
  //                       }
  //                     }
  //                     case Failure(ex2) => Invalid(runId, UnsupportedCommandIssue(" "))
  //                   }
  //                 }
  //                 case None => log.info(s"Bgrx Assembly: Lgrip Hcd not available")
  //               }
  //             }
  //             case Invalid(_, issue) => {
  //               // Handle the invalid command issue
  //               // You can log, handle errors, or take other actions
  //               log.error(s"Bgrx Assembly: Command $runId execution failed with error: $issue")
  //             }
  //             case _ => Invalid(runId, UnsupportedCommandIssue(" "))
  //           }
  //         }
  //         case Failure(ex) => Invalid(runId, UnsupportedCommandIssue(" "))
  //       }
  //       Started(runId)
  //     }
  //     case None => Invalid(runId, UnsupportedCommandIssue("Hcd is not available"))
  //   }
  // }

  private def moveRgripHcd(runId: Id, setup: Setup): Unit = {
    rgripHcdCS match {
      case Some(cs) => {
        val response: Future[SubmitResponse] = cs.submit(setup)
        response.onComplete {
          case Success(result) => {
            result match {
              case Completed(_, _) => {
                // Perform actions for a completed command
                // You can log, update state, or send other messages as needed
                log.info(s"Bgrx Assembly: Command $runId is executed successfully")
                log.info(s"Bgrx Assembly: RgipHcd has moved to exchange position")
                moveLgripHcd(runId)
              }

              case Invalid(_, issue) => {
                log.info(s"Bgrx Assembly: Command $runId execution failure with error - $issue")
                Invalid(runId, issue)
              }

              case _ => Invalid(runId, _)
            }
          }
          case Failure(error) => {
            log.info(s"$error")
            Invalid(runId, UnsupportedCommandIssue(""))
          }
        }
      }
      case None => Invalid(runId, UnsupportedCommandIssue("RgripHcd is not available"))
    }
  }

  private def moveLgripHcd(runId: Id): Unit = {
    val targetPosition = LgripInfo.targetPositionKey.set(100)
    val setup2: Setup  = Setup(sourcePrefix, CommandName("move"), Some(obsId)).madd(targetPosition)

    lgripHcdCS match {
      case Some(cs) => cs.submit(setup2)
      case None     => Invalid(runId, UnsupportedCommandIssue("LgripHcd is not avilable"))
    }
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
