package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlModelFactory;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateTransition;
import org.eclipse.tracecompass.incubator.coherence.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

/**
 * An extension of TmfXmlScenario for managing events coherence checking at the state machine level
 *
 * @author mmartin
 *
 */
public class TmfXmlScenarioObserverNaive extends TmfXmlScenarioObserver {
		
    /**
     * Constructor
     *
     * @param event
     * @param patternHandler
     * @param fsmId
     * @param container
     * @param modelFactory
     */
    public TmfXmlScenarioObserverNaive(@Nullable ITmfEvent event, 
    		@NonNull TmfXmlPatternEventHandler patternHandler, 
    		@NonNull String fsmId, 
    		@NonNull IXmlStateSystemContainer container, 
    		@NonNull ITmfXmlModelFactory modelFactory) {
        super(event, patternHandler, fsmId, container, modelFactory);
    }
    
    @Override
    protected boolean checkEvent(ITmfEvent event) {
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
	        			// Save the possible transition
                        TmfXmlFsmTransition fsmTransition = new TmfXmlFsmTransition(stateTransition, state, event.getName());
                        currentPossibleTransitions.add(fsmTransition);
                    }
                }
            }
        }

        return isCoherent;
    }
    
}
