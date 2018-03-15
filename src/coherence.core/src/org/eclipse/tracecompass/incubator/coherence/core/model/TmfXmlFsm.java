/*******************************************************************************
 * Copyright (c) 2016 Ecole Polytechnique de Montreal, Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.tracecompass.incubator.coherence.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.FsmStateIncoherence;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlScenarioObserver;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.ITmfLostEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.util.Pair;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * This Class implements a state machine (FSM) tree in the XML-defined state
 * system.
 *
 * @author Jean-Christian Kouame
 */
public class TmfXmlFsm {

    protected final Map<String, TmfXmlState> fStatesMap;
    protected final Map<String, TmfXmlScenario> fActiveScenariosList;
    protected final List<TmfXmlBasicTransition> fPreconditions;
    protected final String fId;
    protected final ITmfXmlModelFactory fModelFactory;
    protected final IXmlStateSystemContainer fContainer;
    protected final String fFinalStateId;
    protected final String fAbandonStateId;
    protected final boolean fInstanceMultipleEnabled;
    protected final String fInitialStateId;
    protected final boolean fConsuming;
    protected boolean fEventConsumed;
    protected int fTotalScenarios;
    protected @Nullable TmfXmlScenario fPendingScenario;

    protected boolean fHasIncoherence;              /* indicates if there is at least one possible transition that could have been taken */
    protected boolean fCoherenceCheckingNeeded;     /* indicates if we need to keep on checking the coherence for the current event */
    protected int transitionCount;					/* counter representing the number of transitions taken for the current event */
    
    Map<String, Set<String>> fPrevStates;
	Map<String, Set<String>> fNextStates;
	Map<String, Set<TmfXmlFsmTransition>> fPrevStatesForState;
	
	private Map<TmfXmlFsmTransition, Long> fTransitionsCounters = new HashMap<>();
	
	private String fCoherenceAlgo;
	
	private List<FsmStateIncoherence> incoherences = new ArrayList<>();
	private Map<FsmStateIncoherence, Set<TmfXmlFsmTransition>> possibleTransitionsMap = new HashMap<>(); // temporarily save the possible transitions for each incoherence, before processing
	
	private Map<Pair<String, String>, Set<String>> certaintyMap = new HashMap<>(); // map a pair of (event name, condition name) to a list of unique target state names
	
	/**
	 * Increase the counter of the given transition
	 * @param transition
	 * 				A triggered transition
	 */
	public void increaseTransitionCounter(TmfXmlFsmTransition transition) {
		Long value = (fTransitionsCounters.containsKey(transition)) ? fTransitionsCounters.get(transition) + 1 : 1;
		fTransitionsCounters.put(transition, value);
	}
	
	public void addProblematicEvent(ITmfEvent event, String scenarioAttribute, Set<TmfXmlFsmTransition> transitions, String currentState, ITmfEvent lastEvent) {
	    FsmStateIncoherence incoherence = new FsmStateIncoherence(event, scenarioAttribute, lastEvent, currentState);
	    if (!incoherences.contains(incoherence)) {
	    	incoherences.add(incoherence);
	    	Set<TmfXmlFsmTransition> possibleTransitions = new HashSet<>(); // copy transitions because it will be disposed by the scenario observer
	    	possibleTransitions.addAll(transitions);
	    	possibleTransitionsMap.put(incoherence, possibleTransitions);
	    }
	}
	
	private TmfXmlFsmTransition findBestTransition(Set<TmfXmlFsmTransition> possibleTransitions, Map<TmfXmlFsmTransition, Long> counters, boolean isGlobal) {
		TmfXmlFsmTransition bestTransition = null;
		if (isGlobal) {
			for (TmfXmlFsmTransition t : possibleTransitions) {			
		    	if ((fTransitionsCounters.containsKey(t)) && 
		    			((bestTransition == null) || (fTransitionsCounters.get(t) > fTransitionsCounters.get(bestTransition)))) {
		    		bestTransition = t;
		    	}
		    }
		}
		else {
			for (TmfXmlFsmTransition t : possibleTransitions) {			
		    	if ((counters.containsKey(t)) && 
		    			((bestTransition == null) || (counters.get(t) > counters.get(bestTransition))) || 
	    			(!counters.containsKey(t)) && (fTransitionsCounters.containsKey(t)) && // if we are using per-object statistics but the transition is not in the map, we try to find it in the global map
		    			((bestTransition == null) || (fTransitionsCounters.get(t) > fTransitionsCounters.get(bestTransition)))) { // TODO what happens if bestTransition was found using local object statistics? does it make sense to compare counters from global statistics?
		    		bestTransition = t;
		    	}
		    }
		}
	    
	    if (bestTransition == null) { // every possible transition has never been taken in this fsm
	    	bestTransition = possibleTransitions.iterator().next(); // select first transition
	    }
	    return bestTransition;
	}
	
	/**
	 * Compute the shortest path from the most recent known state (starting node)
	 * to the last known current state (target node), using Dijkstra's algorithm
	 * @param start
	 * 			The starting state
	 * @param target
	 * 			The targeted state
	 * @param counters 
	 * 			The local statistics on transitions, on a per-object basis
	 * 
	 * @return
	 * 			The list of inferred transitions, which is the list of each edge on the shortest path
	 */
	private List<TmfXmlFsmTransition> computeMissingTransitions(String start, String target, 
			Map<TmfXmlFsmTransition, Long> counters, boolean isGlobal) {
		Float INFINITY = Float.POSITIVE_INFINITY; // biggest possible distance
		TmfXmlFsmTransition UNDEFINED = new TmfXmlFsmTransition(null, null, null);
		/* Initialization */
		String current = start;							 			// current node
		Map<String, Float> distances = new HashMap<>(); 			// associates a node (state) to its tentative distance to the start
		List<String> unvisited = new ArrayList<>();					// set of unvisited nodes
		/* the comparator used to sort the list of unvisited nodes according to their distance to start  */
		Comparator<String> compUnvisited = new Comparator<String>() {
			
			@Override
			public int compare(String o1, String o2) {
				if (distances.get(o1) == distances.get(o2) ) {
					return 0;
				}
				return (distances.get(o1) <= distances.get(o2)) ? -1 : 1;
			}
		};
		Map<String, TmfXmlFsmTransition> prev = new HashMap<>(); 	// associates a node to the previous optimal node (with the related transition)
		for (TmfXmlState state : fStatesMap.values()) {
			distances.put(state.getId(), INFINITY); // set to infinity because the distance is unknown
			unvisited.add(state.getId());
			prev.put(state.getId(), UNDEFINED);
		}
		distances.put(current, 0f); // the current node is the start ; distance to itself is 0
		
		while (unvisited.contains(target)) {
			float currentDist = distances.get(current);
			Set<TmfXmlFsmTransition> neighbors = fPrevStatesForState.get(current);
			 /* Check the neighbors of the current node */
			for (TmfXmlFsmTransition neighborTransition : neighbors) {
				String neighbor = neighborTransition.from().getId();
				/* Evaluate path to target using the statistics on transitions from the reading of the trace */
				long weight;
				if (isGlobal || !counters.containsKey(neighborTransition)) { // if we are using per-object statistics but the transition is not in the map, we try to find it in the global map
					weight = fTransitionsCounters.containsKey(neighborTransition) ? fTransitionsCounters.get(neighborTransition) : 1;
				}
				else {
					weight = counters.get(neighborTransition);
				}
				float newDist = currentDist + (1f / (float) weight); 
				if (distances.get(neighbor) > newDist) {	// this is a shorter path to target
					distances.put(neighbor, newDist);
					prev.put(neighbor, neighborTransition);
				}
			}
			
			/* Remove current node from the list of unvisited nodes */
			unvisited.remove(current);
			/* Look for the next node, which is the unvisited node with minimum distance to start */
			unvisited.sort(compUnvisited);
			if (!unvisited.isEmpty()) {
				current = unvisited.get(0);
			}
		}		
		/* The target node has been reached */
		
		/* Compute the path, given the results obtained at previous steps,
		 * by following the nodes in the 'prev' map from target to start
		 */
		Stack<TmfXmlFsmTransition> transitions = new Stack<>();
		String node = target;
		while (!prev.get(node).equals(UNDEFINED)) { // we should reach an UNDEFINED value when reaching the starting node
			TmfXmlFsmTransition transition = prev.get(node);
			transitions.push(transition);
			node = transition.to().getTarget();
		}
		return transitions;
	}
	
	/**
	 * Select a transition from the list of possible transitions for each incoherent event
	 * We select the most probable transition, considering that the most frequent one is the most probable
	 * @return 
	 * 			The status of this operation
	 */
	public void setTransitions() {
		for (FsmStateIncoherence incoherence : incoherences) {
			String targetState = incoherence.getLastCoherentStateName();
			Set<TmfXmlFsmTransition> possibleTransitions = possibleTransitionsMap.get(incoherence);
			// Infer transitions
			Map<TmfXmlFsmTransition, Long> counters = ((TmfXmlScenarioObserver) fActiveScenariosList.get(incoherence.getScenarioAttribute())).getTransitionsCounters();
			boolean isGlobal = false; // FIXME: hard-coded parameter
			TmfXmlFsmTransition lastTransition = findBestTransition(possibleTransitions, counters, isGlobal);
			List<TmfXmlFsmTransition> inferredTransitions = new ArrayList<>();
	    	inferredTransitions.addAll(computeMissingTransitions(lastTransition.from().getId(), targetState, counters, 
	    			isGlobal));
			inferredTransitions.add(lastTransition);
			incoherence.setInferredTransitions(inferredTransitions);
		}
	}
	
	public List<FsmStateIncoherence> getIncoherences() {
		return incoherences;
	}
	
	public List<ITmfEvent> getIncoherentEvents() {
		List<ITmfEvent> incoherentEvents = new ArrayList<>();
		for (FsmStateIncoherence incoherence : incoherences) {
			incoherentEvents.add(incoherence.getIncoherentEvent());
		}
		return incoherentEvents;
	}

    public int getTransitionCount() {
		return transitionCount;
	}

	public void increaseTransitionCount() {
		transitionCount++;
	}

	/**
     * Factory to create a {@link TmfXmlFsm}
     *
     * @param modelFactory
     *            The factory used to create XML model elements
     * @param node
     *            The XML root of this fsm
     * @param container
     *            The state system container this fsm belongs to
     * @return The new {@link TmfXmlFsm}
     */
    public static TmfXmlFsm create(ITmfXmlModelFactory modelFactory, Element node, IXmlStateSystemContainer container) {
        String id = node.getAttribute(TmfXmlStrings.ID);
        boolean consuming = node.getAttribute(TmfXmlStrings.CONSUMING).isEmpty() ? true : Boolean.parseBoolean(node.getAttribute(TmfXmlStrings.CONSUMING));
        boolean instanceMultipleEnabled = node.getAttribute(TmfXmlStrings.MULTIPLE).isEmpty() ? true : Boolean.parseBoolean(node.getAttribute(TmfXmlStrings.MULTIPLE));
        final List<@NonNull TmfXmlBasicTransition> preconditions = new ArrayList<>();

        // Get the preconditions
        NodeList nodesPreconditions = node.getElementsByTagName(TmfXmlStrings.PRECONDITION);
        for (int i = 0; i < nodesPreconditions.getLength(); i++) {
            preconditions.add(new TmfXmlBasicTransition(((Element) NonNullUtils.checkNotNull(nodesPreconditions.item(i)))));
        }

        // Get the initial state and the preconditions
        Map<@NonNull String, @NonNull TmfXmlState> statesMap = new HashMap<>();
        String initialState = node.getAttribute(TmfXmlStrings.INITIAL);
        NodeList nodesInitialElement = node.getElementsByTagName(TmfXmlStrings.INITIAL);
        NodeList nodesInitialStateElement = node.getElementsByTagName(TmfXmlStrings.INITIAL_STATE);
        if (nodesInitialStateElement.getLength() > 0) {
            if (!initialState.isEmpty() || nodesInitialElement.getLength() > 0) {
                Activator.logWarning("Fsm " + id + ": the 'initial' attribute was set or an <initial> element was defined. Only one of the 3 should be used."); //$NON-NLS-1$ //$NON-NLS-2$
            }
            @NonNull TmfXmlState initial = modelFactory.createState((Element) nodesInitialStateElement.item(0), container, null);
            statesMap.put(TmfXmlState.INITIAL_STATE_ID, initial);
            initialState = TmfXmlState.INITIAL_STATE_ID;
        } else {
            if (!initialState.isEmpty() && nodesInitialElement.getLength() > 0) {
                Activator.logWarning("Fsm " + id + " was declared with both 'initial' attribute and <initial> element. Only the 'initial' attribute will be used"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            if (initialState.isEmpty() && nodesInitialElement.getLength() > 0) {
                    NodeList nodesTransition = ((Element) nodesInitialElement.item(0)).getElementsByTagName(TmfXmlStrings.TRANSITION);
                    if (nodesInitialElement.getLength() != 1) {
                        throw new IllegalArgumentException("initial element : there should be one and only one initial state."); //$NON-NLS-1$
                    }
                    initialState = ((Element) nodesTransition.item(0)).getAttribute(TmfXmlStrings.TARGET);
            }
        }

        // Get the FSM states
        NodeList nodesState = node.getElementsByTagName(TmfXmlStrings.STATE);
        for (int i = 0; i < nodesState.getLength(); i++) {
            Element element = (Element) NonNullUtils.checkNotNull(nodesState.item(i));
            TmfXmlState state = modelFactory.createState(element, container, null);
            statesMap.put(state.getId(), state);

            // If the initial state was not already set, we use the first state
            // declared in the fsm description as initial state
            if (initialState.isEmpty()) {
                initialState = state.getId();
            }
        }

        if (initialState.isEmpty()) {
            throw new IllegalStateException("No initial state has been declared in fsm " + id); //$NON-NLS-1$
        }

        // Get the FSM final state
        String finalStateId = TmfXmlStrings.NULL;
        NodeList nodesFinalState = node.getElementsByTagName(TmfXmlStrings.FINAL);
        if (nodesFinalState.getLength() == 1) {
            final Element finalElement = NonNullUtils.checkNotNull((Element) nodesFinalState.item(0));
            finalStateId = finalElement.getAttribute(TmfXmlStrings.ID);
            if (!finalStateId.isEmpty()) {
                TmfXmlState finalState = modelFactory.createState(finalElement, container, null);
                statesMap.put(finalState.getId(), finalState);
            }
        }

        // Get the FSM abandon state
        String abandonStateId = TmfXmlStrings.NULL;
        NodeList nodesAbandonState = node.getElementsByTagName(TmfXmlStrings.ABANDON_STATE);
        if (nodesAbandonState.getLength() == 1) {
            final Element abandonElement = NonNullUtils.checkNotNull((Element) nodesAbandonState.item(0));
            abandonStateId = abandonElement.getAttribute(TmfXmlStrings.ID);
            if (!abandonStateId.isEmpty()) {
                TmfXmlState abandonState = modelFactory.createState(abandonElement, container, null);
                statesMap.put(abandonState.getId(), abandonState);
            }
        }

        Map<String, Set<String>> prevStates = new HashMap<>();
        Map<String, Set<String>> nextStates = new HashMap<>();
        Map<String, Set<TmfXmlFsmTransition>> prevStatesForState = new HashMap<>();
        Map<Pair<String, String>, Set<String>> certaintyInfo = new HashMap<>();
        
        // Create the maps of previous states and next states
        for (TmfXmlState state : statesMap.values()) {
        	if (!prevStatesForState.containsKey(state.getId())) {
        		prevStatesForState.put(state.getId(), new HashSet<>()); // every state will have at least an empty set of previous states
        	}
	        for (TmfXmlStateTransition transition : state.getTransitionList()) {
	        	for (Pattern pattern : transition.getAcceptedEvents()) {
	        		String eventName = pattern.toString();
	        		// Add a state to the list of previous states for the current event
	        		Set<String> statesId;
	        		if (prevStates.containsKey(eventName)) {
	                	statesId = prevStates.get(eventName);
	        		}
	        		else {
	        			statesId = new HashSet<>();
	        		}
	        		statesId.add(state.getId()); // Set cannot contain duplicate elements, so no need to check
    				prevStates.replace(eventName, statesId);
    				
					// Add a state to the list of next states for the current event
	        		if (nextStates.containsKey(eventName)) {
	        			statesId = nextStates.get(eventName);
	        		}
	        		else {
	        			statesId = new HashSet<>();
	        		}
	        		statesId.add(transition.getTarget());
    				nextStates.replace(eventName, statesId);
    				
    				// Add a state to the list of previous states for the target state
    				TmfXmlFsmTransition fsmTransition = new TmfXmlFsmTransition(transition, state, eventName);
    				String targetState = transition.getTarget();
    				Set<TmfXmlFsmTransition> set = (prevStatesForState.containsKey(targetState)) ? prevStatesForState.get(targetState) : new HashSet<>();
    				set.add(fsmTransition);
    				prevStatesForState.put(targetState, set);
    				
    				// Add a certainty information
    				Pair<String, String> p = new Pair<>(eventName, transition.getCondition());
    				Set<String> targets = certaintyInfo.containsKey(p) ? certaintyInfo.get(p) : new HashSet<>();
    				targets.add(targetState);
    				certaintyInfo.put(p, targets);
				}
			}
        }
        
        return new TmfXmlFsm(modelFactory, container, id, consuming, instanceMultipleEnabled, initialState, finalStateId, 
        		abandonStateId, preconditions, statesMap, prevStates, nextStates, prevStatesForState, certaintyInfo);
    }

    protected TmfXmlFsm(ITmfXmlModelFactory modelFactory, IXmlStateSystemContainer container, String id, boolean consuming,
            boolean multiple, String initialState, String finalState, String abandonState, List<TmfXmlBasicTransition> preconditions,
            Map<String, TmfXmlState> states, Map<String, Set<String>> prevStates, Map<String, Set<String>> nextStates, 
            Map<String, Set<TmfXmlFsmTransition>> prevStatesForState, Map<Pair<String, String>, Set<String>> certaintyInfo) {
        fModelFactory = modelFactory;
        fTotalScenarios = 0;
        fContainer = container;
        fId = id;
        fConsuming = consuming;
        fInstanceMultipleEnabled = multiple;
        fInitialStateId = initialState;
        fFinalStateId = finalState;
        fAbandonStateId = abandonState;
        fPreconditions = ImmutableList.copyOf(preconditions);
        fStatesMap = ImmutableMap.copyOf(states);
        fActiveScenariosList = new LinkedHashMap<>();
        fPrevStates = prevStates;
        fNextStates = nextStates;
        fPrevStatesForState = prevStatesForState;
        certaintyMap = certaintyInfo;
    }
    
    public Map<String, Set<String>> getPrevStates() {
		return fPrevStates;
	}    
    
    public Map<String, Set<String>> getNextStates() {
		return fNextStates;
	}

    /**
     * Get the fsm ID
     *
     * @return the id of this fsm
     */
    public String getId() {
        return fId;
    }

    /**
     * Get the initial state ID of this fsm
     *
     * @return the id of the initial state of this finite state machine
     */
    public String getInitialStateId() {
        return fInitialStateId;
    }

    /**
     * Get the final state ID of this fsm
     *
     * @return the id of the final state of this finite state machine
     */
    public String getFinalStateId() {
        return fFinalStateId;
    }

    /**
     * Get the abandon state ID fo this fsm
     *
     * @return the id of the abandon state of this finite state machine
     */
    public String getAbandonStateId() {
        return fAbandonStateId;
    }

    /**
     * Get the states table of this fsm in map
     *
     * @return The map containing all state definition for this fsm
     */
    public Map<String, TmfXmlState> getStatesMap() {
        return Collections.unmodifiableMap(fStatesMap);
    }


    /**
     * Get the active scenarios of this fsm
     * @return The list of the active scenarios
     */
    public Map<String, TmfXmlScenario> getActiveScenariosList() {
        return fActiveScenariosList;
    }


    /**
     * Get the preconditions of this fsm
     *
     * @return The list of preconditions
     */
    public List<TmfXmlBasicTransition> getPreconditions() {
        return fPreconditions;
    }

    /**
     * Get whether or not this fsm can have multiple instances
     *
     * @return True if there can be multiple instances, false otherwise
     */
    public boolean isInstanceMultipleEnabled() {
        return fInstanceMultipleEnabled;
    }

    /**
     * Get whether or not this fsm consumes events
     *
     * @return True if the fsm is consuming, false otherwise
     */
    public boolean isConsuming() {
        return fConsuming;
    }

    /**
     * Set whether the ongoing was consumed by a scenario or not
     *
     * @param eventConsumed
     *            The consumed state
     */
    public void setEventConsumed(boolean eventConsumed) {
        fEventConsumed = eventConsumed;
    }

    /**
     * Get whether or not the current event has been consumed
     *
     * @return True if the event has been consumed, false otherwise
     */
    protected boolean isEventConsumed() {
        return fEventConsumed;
    }

    /**
     * Set whether the coherence needs to be checked
     *
     * @param coherenceCheckingNeeded
     *            The value
     */
    public void setCoherenceCheckingNeeded(boolean coherenceCheckingNeeded) {
        fCoherenceCheckingNeeded = coherenceCheckingNeeded;
    }

    /**
     * Select a new algorithm for the scenario observers instead of the default one
     * @param algoId
     * 			The id of the algorithm to use
     */
    public void setCoherenceAlgorithm(String algoId) {
    	fCoherenceAlgo = algoId;
    }

    /**
     * Process the active event and determine the next step of this fsm
     *
     * @param event
     *            The event to process
     * @param tests
     *            The list of possible transitions of the state machine
     * @param scenarioInfo
     *            The active scenario details.
     * @return A pair containing the next state of the state machine and the
     *         actions to execute
     */
    public @Nullable TmfXmlStateTransition next(ITmfEvent event, Map<String, TmfXmlTransitionValidator> tests, TmfXmlScenarioInfo scenarioInfo) {
        boolean matched = false;
        TmfXmlStateTransition stateTransition = null;
        TmfXmlState state = fStatesMap.get(scenarioInfo.getActiveState());
        if (state == null) {
            /** FIXME: This logging should be replaced by something the user will see, this is XML debugging information! */
            Activator.logError(NLS.bind(Messages.TmfXmlFsm_StateUndefined, scenarioInfo.getActiveState(), getId()));
            return null;
        }
        for (int i = 0; i < state.getTransitionList().size() && !matched; i++) {
            stateTransition = state.getTransitionList().get(i);
            matched = stateTransition.test(event, scenarioInfo, tests);
        }
        return matched ? stateTransition : null;
    }



    /**
     * Validate the preconditions of this fsm. If not validate, the fsm will
     * skip the active event.
     *
     * @param event
     *            The current event
     * @param tests
     *            The transition inputs
     * @return True if one of the precondition is validated, false otherwise
     */
    public boolean validatePreconditions(ITmfEvent event, Map<String, TmfXmlTransitionValidator> tests) {
        if (fPreconditions.isEmpty()) {
            return true;
        }
        for (TmfXmlBasicTransition precondition : fPreconditions) {
            if (precondition.test(event, null, tests)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the values of some predefined attributes in an event
     * Here, we collect every attribute related to tid
     * @param event
     * 			The event from which we collect the values
     * @param layout
     * 			The event layout
     * @return
     * 			A list of attribute values as strings
     */
    List<String> getAttributesForEvent(ITmfEvent event, IKernelAnalysisEventLayout layout) {
    	List<String> attributes = new ArrayList<>();
    	// FIXME hardcoded pattern names
    	if (fId.equals("process_fsm")) {
	    	ITmfEventField content = event.getContent();
	    	
	    	// We want to collect tid information for process FSM
	    	if (event.getName().equals("sched_switch")) {
		    	ITmfEventField prevTid = content.getField(layout.fieldPrevTid());
		    	if (prevTid != null) {
		    		attributes.add(prevTid.getValue().toString());
		    	}
		    	
		    	ITmfEventField nextTid = content.getField(layout.fieldNextTid());
		    	if (nextTid != null) {
		    		attributes.add(nextTid.getValue().toString());
		    	}
	    	}
	    	else {
		    	ITmfEventField childTid = content.getField(layout.fieldChildTid());
		    	if (childTid != null) {
		    		attributes.add(childTid.getValue().toString());
		    	}
		    	
				Object tidAspect = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), KernelTidAspect.class, event);
				if (tidAspect != null) {
					attributes.add(((Integer) tidAspect).toString());
				}
	    	}
    	}
    	else if (fId.equals("cpu_fsm") || fId.equals("softirq_fsm")) {
			Object cpuAspect = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
			if (cpuAspect != null) {
				attributes.add(((Integer) cpuAspect).toString());
			}
    	}
    	else if (fId.equals("irq_fsm")) {
    		Object tidAspect = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), KernelTidAspect.class, event);
			if (tidAspect != null) {
				attributes.add(((Integer) tidAspect).toString());
			}
    	}
    	
    	return attributes;
    }
    
    private int computeRequiredTransitions(ITmfEvent event, IKernelAnalysisEventLayout layout) {
    	return event.getName().equals(layout.eventSchedSwitch()) ? 2 : 1;
    }
    
    /**
     * Handle the current event
     *
     * @param event
     *            The current event
     * @param testMap
     *            The transitions of the pattern
     */
    public void handleEvent(ITmfEvent event, Map<String, TmfXmlTransitionValidator> testMap, boolean startChecking, 
    		IKernelAnalysisEventLayout layout) {
        setEventConsumed(false);
        fCoherenceCheckingNeeded = startChecking;
        
        // Initialize the counters
        int transitionTotal = computeRequiredTransitions(event, layout);
        transitionCount = 0;
        
        // Handle only the scenarios related to this event, which are identified by the tid of the process it models
        List<String> eventAttributes = getAttributesForEvent(event, layout);
        if (event instanceof ITmfLostEvent) { // check certainty here
        	for (TmfXmlScenario scenario : fActiveScenariosList.values()) {
        		scenario.updateCertainty(false, event.getTimestamp().getValue());
        	}
        }
        else {
	        for (String attr : eventAttributes) {
	        	TmfXmlScenario scenario = fActiveScenariosList.get(attr);
	        	if (scenario != null) {
	        		handleScenario(scenario, event, fCoherenceCheckingNeeded, transitionTotal);
	        	}
	        }
        }
        
        if (!eventAttributes.isEmpty()) { // if we did not find any attribute for this event, it means it should be applied to no scenario
	        boolean isValidInput = validatePreconditions(event, testMap);
	        handlePendingScenario(event, isValidInput, transitionTotal);
        }
    }

    /**
     * Handle the pending scenario.
     *
     * @param event
     *            The ongoing event
     * @param isInputValid
     *            Either the ongoing event validated the preconditions or not
     */
    private void handlePendingScenario(ITmfEvent event, boolean isInputValid, int transitionTotal) {
        if (fConsuming && isEventConsumed()) {
            return;
        }

        TmfXmlScenario scenario = fPendingScenario;
        if ((fInitialStateId.equals(TmfXmlState.INITIAL_STATE_ID) || isInputValid) && scenario != null) {
        	scenario.updateCertainty(false, ((AbstractTmfStateProvider) fContainer).getTrace().getStartTime().getValue());
            handleScenario(scenario, event, fCoherenceCheckingNeeded, transitionTotal);
            if (!scenario.isPending()) {
                addActiveScenario(scenario);
                fPendingScenario = null;
            }
        }
    }

    /**
     * Abandon all ongoing scenarios
     */
    public void dispose() {
        for (TmfXmlScenario scenario : fActiveScenariosList.values()) {
            if (scenario.isActive()) {
                scenario.cancel();
            }
        }
    }

    protected static void handleScenario(TmfXmlScenario scenario, ITmfEvent event, boolean isCoherenceCheckingNeeded, int transitionTotal) {
        if (scenario.isActive() || scenario.isPending()) {
        	scenario.handleEvent(event, isCoherenceCheckingNeeded, transitionTotal);
        }
    }

    /**
     * Create a new scenario of this fsm
     *
     * @param event
     *            The current event, null if not
     * @param eventHandler
     *            The event handler this fsm belongs
     * @param force
     *            True to force the creation of the scenario, false otherwise
     */
    public synchronized void createScenario(@Nullable ITmfEvent event, TmfXmlPatternEventHandler eventHandler, boolean force, 
    		boolean isObserver) {
        if (force || isNewScenarioAllowed()) {
            fTotalScenarios++;
            if (isObserver) {
            	fPendingScenario = new TmfXmlScenarioObserver(event, eventHandler, fId, fContainer, fModelFactory, fCoherenceAlgo);
            }
            else {
            	fPendingScenario = new TmfXmlScenario(event, eventHandler, fId, fContainer, fModelFactory);
            }
        }
    }

    /**
     * Add a scenario to the active scenario list
     *
     * @param scenario
     *            The scenario
     */
    private void addActiveScenario(TmfXmlScenario scenario) {
        fActiveScenariosList.put(scenario.getAttribute(), scenario);
    }

    /**
     * Check if we have the right to create a new scenario. A new scenario could
     * be created if it is not the first scenario of an FSM and the FSM is not a
     * singleton and the status of the last created scenario is not PENDING.
     *
     * @return True if the start of a new scenario is allowed, false otherwise
     */
    public synchronized boolean isNewScenarioAllowed() {
        return fTotalScenarios > 0 && fInstanceMultipleEnabled
                && fPendingScenario == null;
    }

    /**
     * Determine if an event causes the state to be coherent with certainty
     * A state A becomes certain when an event e is observed if e labels one or several transitions to A only  
     * 
     * @param event
 * 				The event who labels the taken transition 
     * @param transition
 * 				The taken transition
 * 
     * @return
     * 			The certainty value (true if certain, false if uncertain)		
     */
    public boolean isCertain(ITmfEvent event, TmfXmlStateTransition transition) {
    	Pair<String, String> key = new Pair<String, String>(event.getName(), transition.getCondition());
    	Set<String> targets = certaintyMap.get(key);
    	return targets != null && targets.size() == 1 ? true : false;
    }
}
