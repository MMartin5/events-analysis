package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlAction;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlModelFactory;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenario;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder.ScenarioStatusType;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateTransition;
import org.eclipse.tracecompass.incubator.coherence.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfLostEvent;

/**
 * An extension of TmfXmlScenario for managing events coherence checking at the state machine level
 *
 * @author mmartin
 *
 */
public class TmfXmlScenarioObserver extends TmfXmlScenario {

    /**
     * Constructor
     *
     * @param event
     * @param patternHandler
     * @param fsmId
     * @param container
     * @param modelFactory
     */
    public TmfXmlScenarioObserver(@Nullable ITmfEvent event, @NonNull TmfXmlPatternEventHandler patternHandler, @NonNull String fsmId, @NonNull IXmlStateSystemContainer container, @NonNull ITmfXmlModelFactory modelFactory) {
        super(event, patternHandler, fsmId, container, modelFactory);
    }

    /**
     * Check if the event is coherent or not
     * An event is coherent if a transition from the current state can be taken, or if no transition can be taken at all
     * It is incoherent if a transition could have been taken if the state machine was in a state which is not the current one
     *
     * @param event
     *            The event to check
     *
     * @return True if event is coherent, false otherwise
     */
    private boolean checkEvent(ITmfEvent event) {
        boolean isCoherent = true;

        Map<String, TmfXmlState> states = fFsm.getStatesMap();
        TmfXmlState currentState = states.get(fScenarioInfo.getActiveState());

        if (currentState == null) {
            return false;
        }

        TmfXmlStateTransition stateTransition = null;

        // We check every state of the FSM
        for (TmfXmlState state : states.values()) {
            // We check every transition of the state
            for (int i = 0; i < state.getTransitionList().size(); i++) {
                stateTransition = state.getTransitionList().get(i);
                if (stateTransition.test(event, fScenarioInfo, fPatternHandler.getTestMap())) { // true if the transition can be taken
                    if (!state.getId().equals(currentState.getId())) {
                        /* A transition could have been taken from another state */
                        isCoherent = false;
                    }
                }
            }
        }

        return isCoherent;
    }

    @Override
    public void handleEvent(ITmfEvent event, boolean isCoherenceCheckingNeeded) {

        if (event instanceof ITmfLostEvent) {
        	// We start checking the coherence of events when we receive the first 'Lost event'
        	fPatternHandler.setStartChecking(true);
        }

        TmfXmlStateTransition out = fFsm.next(event, fPatternHandler.getTestMap(), fScenarioInfo);
        if (out == null) {
            /* If there is no transition and checking is needed, we need to check the coherence of the event */
            if (isCoherenceCheckingNeeded && !checkEvent(event)) {
                fFsm.setEventCoherent(false); // this event might be incoherent but we need to keep on checking for other scenarios
            }
            return;
        }

        /* If there is one transition, then this event is coherent for all scenarios and checking can be stopped */
        fFsm.setEventCoherent(true);
        fFsm.setCoherenceCheckingNeeded(false);

        fFsm.setEventConsumed(true);
        // Processing the actions in the transition
        final List<String> actions = out.getAction();
        for (String actionId : actions) {
            ITmfXmlAction action = fPatternHandler.getActionMap().get(actionId);
            if (action != null) {
                action.execute(event, fScenarioInfo);
            } else {
                Activator.logError("Action " + actionId + " cannot be found."); //$NON-NLS-1$ //$NON-NLS-2$
                return;
            }
        }

        // Change the activeState
        final @NonNull String nextState = out.getTarget();
        if (fScenarioInfo.getStatus().equals(ScenarioStatusType.PENDING)) {
            fScenarioInfo.setStatus(ScenarioStatusType.IN_PROGRESS);
            fHistoryBuilder.startScenario(fContainer, fScenarioInfo, event);
        } else if (nextState.equals(fFsm.getAbandonStateId())) {
            fScenarioInfo.setStatus(ScenarioStatusType.ABANDONED);
            fHistoryBuilder.completeScenario(fContainer, fScenarioInfo, event);
        } else if (nextState.equals(fFsm.getFinalStateId())) {
            fScenarioInfo.setStatus(ScenarioStatusType.MATCHED);
            fHistoryBuilder.completeScenario(fContainer, fScenarioInfo, event);
        }
        fScenarioInfo.setActiveState(nextState);
        fHistoryBuilder.update(fContainer, fScenarioInfo, event);
    }

}
