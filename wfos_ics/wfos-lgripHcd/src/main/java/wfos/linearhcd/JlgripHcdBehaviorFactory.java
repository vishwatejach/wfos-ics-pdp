package wfos.lgripHcd;

import akka.actor.typed.javadsl.ActorContext;
import csw.command.client.messages.TopLevelActorMessage;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.time.core.models.UTCTime;
import csw.params.core.models.Id;


import java.util.concurrent.CompletableFuture;

public class JlgripHcdBehaviorFactory extends JComponentHandlers {

    private final JCswContext cswCtx;
    private final ILogger log;

    JlgripHcdBehaviorFactory(ActorContext<TopLevelActorMessage> ctx,JCswContext cswCtx) 
    {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());
    }

    @Override
    public void initialize() {
    log.info("Initializing lgrip HCD...");
    }

    @Override
    public void onShutdown() {
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {

    }

    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(Id runId, ControlCommand controlCommand) {
        return new CommandResponse.Accepted(runId);
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(Id runId, ControlCommand controlCommand) {
        return new CommandResponse.Completed(runId);
    }

    @Override
    public void onOneway(Id runId, ControlCommand controlCommand) {

    }

    @Override
    public void onGoOffline() {

    }

    @Override
    public void onGoOnline() {

    }

    @Override
    public void onDiagnosticMode(UTCTime startTime, String hint){

    }

    @Override
    public void onOperationsMode() {

    }
}
