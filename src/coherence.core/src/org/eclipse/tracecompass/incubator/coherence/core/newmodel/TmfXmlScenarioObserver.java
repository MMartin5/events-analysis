package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlAction;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlModelFactory;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenario;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioInfo;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder.ScenarioStatusType;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateTransition;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlTransitionValidator;
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
	
	ITmfEvent lastEvent; // the last event having triggered a transition
	Set<TmfXmlFsmTransition> currentPossibleTransitions = new HashSet<>();
	Method checkMethod;
	
	public static String ALGO1 = "checkEvent";
	public static String ALGO2 = "checkEvent2";
	
    /**
     * Constructor
     *
     * @param event
     * @param patternHandler
     * @param fsmId
     * @param container
     * @param modelFactory
     */
    public TmfXmlScenarioObserver(@Nullable ITmfEvent event, @NonNull TmfXmlPatternEventHandler patternHandler, @NonNull String fsmId, 
    		@NonNull IXmlStateSystemContainer container, @NonNull ITmfXmlModelFactory modelFactory, String algoId) {
        super(event, patternHandler, fsmId, container, modelFactory);
        
        try {
        	Class[] args = new Class[1];
        	args[0] = ITmfEvent.class;
			checkMethod = TmfXmlScenarioObserver.class.getDeclaredMethod(algoId, args);
		} catch (NoSuchMethodException e) {
			Activator.logError("No such algorithm", e);
		} catch (SecurityException e) {
			Activator.logError("SecurityException while trying to get the coherence algorithm", e);
		}
        
        lastEvent = null;
    }
    
    /**
     * Computes the list of every possible transition, including the transition from the current state
     * @param event
     * @param statesMap
     * @param scenarioInfo
     * @param patternHandler
     * @return
     * 			The list of possible transitions
     */
    public static Set<TmfXmlFsmTransition> computePossibleTransitions(ITmfEvent event, 
    		Map<String, TmfXmlState> statesMap, 
    		TmfXmlScenarioInfo scenarioInfo,
    		Map<String, TmfXmlTransitionValidator> testMap) {
    	
    	Set<TmfXmlFsmTransition> possibleTransitions = new HashSet<>();
    	TmfXmlStateTransition stateTransition = null;
    	
    	// We check every state
    	for (TmfXmlState state : statesMap.values()) {
            // We check every transition of the state
            for (int i = 0; i < state.getTransitionList().size(); i++) {
                stateTransition = state.getTransitionList().get(i);
                if (stateTransition.test(event, scenarioInfo, testMap)) { // true if the transition can be taken
                    TmfXmlFsmTransition fsmTransition = new TmfXmlFsmTransition(stateTransition, state, event.getName());
                    possibleTransitions.add(fsmTransition);
                }
            }
        }
    	
    	return possibleTransitions;
    }
    
    public static Set<TmfXmlFsmTransition> computePossibleTransitions(TmfXmlState currentState,	Map<String, TmfXmlState> statesMap) {
    	
    	Set<TmfXmlFsmTransition> possibleTransitions = new HashSet<>();
    	TmfXmlStateTransition stateTransition = null;
    	
    	// We check every state
    	for (TmfXmlState state : statesMap.values()) {
            // We check every transition of the state
            for (int i = 0; i < state.getTransitionList().size(); i++) {
                stateTransition = state.getTransitionList().get(i);
                if (stateTransition.getTarget().equals(currentState.getId())) { // the current state can be reached with this transition
                	for (Pattern eventPattern : stateTransition.getAcceptedEvents()) { // insert one transition per accepted event
                		String eventName = eventPattern.toString();
	                    TmfXmlFsmTransition fsmTransition = new TmfXmlFsmTransition(stateTransition, state, eventName);
	                    possibleTransitions.add(fsmTransition);
                	}
                }
            }
        }
    	
    	return possibleTransitions;
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
	        			// Save the possible transition
                        TmfXmlFsmTransition fsmTransition = new TmfXmlFsmTransition(stateTransition, state, event.getName());
                        currentPossibleTransitions.add(fsmTransition);
                    }
                }
            }
        }

        return isCoherent;
    }
    
    private boolean checkEvent2(ITmfEvent event) {
        boolean isCoherent = true;
        
        Set<String> prevStates = fFsm.getPrevStates().get(event.getName());
        if (prevStates != null) { // we might have a null set if this event is never accepted by any state of the FSM
	        Map<String, TmfXmlState> states = fFsm.getStatesMap();
	        TmfXmlState currentState = states.get(fScenarioInfo.getActiveState());
	        	
	        if (currentState == null) {
	            return false;
	        }
	
	        TmfXmlStateTransition stateTransition = null;
	
	        // We check only in the possible previous states for this event
	        for (String stateName : prevStates) { // TODO: key is the string of a Pattern
	        	TmfXmlState state = states.get(stateName);
	        	if (state == null) { // state is null because stateId in statesMap is not the same as the id of XML state
	        		state = states.get(TmfXmlState.INITIAL_STATE_ID);
	        	}
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
        }

        return isCoherent;
    }

    @Override
    public void handleEvent(ITmfEvent event, boolean isCoherenceCheckingNeeded, int transitionTotal) {
    	// Clear current possible transitions set as we receive a new event
    	currentPossibleTransitions.clear();

        if (event instanceof ITmfLostEvent) {
        	// The entry state becomes uncertain
        	updateCertainty(event);
        	
        	if (!fPatternHandler.startChecking()) {    	
	        	// We start checking the coherence of events when we receive the first 'Lost event'
	        	fPatternHandler.setStartChecking(true);
        	}
        }

        TmfXmlStateTransition out = fFsm.next(event, fPatternHandler.getTestMap(), fScenarioInfo);
        if (out == null) { // No transition from the current state has been found
            /* If there is no transition and checking is needed, we need to check the coherence of the event */
			try {
				if (isCoherenceCheckingNeeded && !((boolean) checkMethod.invoke(this, event))) {
				    // Save incoherences
				    fFsm.addProblematicEvent(event, fAttribute, currentPossibleTransitions, fScenarioInfo.getActiveState(), lastEvent); // currentPossibleTransitions has been set in checkEvent
				}
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				Activator.logError("Error while invoking the method to check event", e);
			}
            return;
        }
        
        // Increase transitions counter
        TmfXmlState currentState = fFsm.getStatesMap().get(fScenarioInfo.getActiveState());
        lastEvent = event;
        TmfXmlFsmTransition fsmTransition = new TmfXmlFsmTransition(out, currentState, event.getName());
        fFsm.increaseTransitionCounter(fsmTransition);
        
        fFsm.increaseTransitionCount(); // we have found a transition from the current state, so we increase the counter on taken transitions

    	if (isCoherenceCheckingNeeded && (fFsm.getTransitionCount() == transitionTotal)) {
    		fFsm.setCoherenceCheckingNeeded(false); // as soon as we find all of the needed transitions, we can stop checking
    	}

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
        
        // Update the certainty status to certain if the transition is appropriate
        if (fFsm.isCertain(event, out)) {
        	fHistoryBuilder.updateCertaintyStatus(fContainer, fScenarioInfo, event);
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
        
        if (fAttribute == null) { // it means this is the first event being handled
        	fAttribute = setAttribute(); // attribute should be set after the fHistoryBuilder.update
        }
    }

}
