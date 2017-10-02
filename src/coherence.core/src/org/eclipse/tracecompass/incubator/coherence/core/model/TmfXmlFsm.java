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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlScenarioObserver;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
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
    protected final List<TmfXmlScenario> fActiveScenariosList;
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

    protected boolean fEventCoherent;               /* indicates if at least one scenario has a transition for the current event */
    protected boolean fCoherenceCheckingNeeded;     /* indicated if we need to keep on checking the coherence for the current event */

    private final List<ITmfEvent> fProblematicEvents = new ArrayList<>();

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
        return new TmfXmlFsm(modelFactory, container, id, consuming, instanceMultipleEnabled, initialState, finalStateId, abandonStateId, preconditions, statesMap);
    }

    protected TmfXmlFsm(ITmfXmlModelFactory modelFactory, IXmlStateSystemContainer container, String id, boolean consuming,
            boolean multiple, String initialState, String finalState, String abandonState, List<TmfXmlBasicTransition> preconditions,
            Map<String, TmfXmlState> states) {
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
        fActiveScenariosList = new ArrayList<>();
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
    public List<TmfXmlScenario> getActiveScenariosList() {
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
     * Get whether or not we need to keep on checking the coherence of the current event
     *
     * @return True if coherence has to be checked, false otherwise
     */
    public boolean isCoherenceCheckingNeeded() {
        return fCoherenceCheckingNeeded;
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
     * Get whether or not the current event is coherent or not
     * @return True if the event is coherent, false if incoherent or unknown
     */
    public boolean isEventCoherent() {
        return fEventCoherent;
    }

    /**
     * Set whether the current event is coherent
     * @param value
     *            The coherency of the event
     */
    public void setEventCoherent(boolean value) {
        fEventCoherent = value;
    }

    /**
     * Get the problematic events found while handling the events
     * @return The list of problematic events
     */
    public List<ITmfEvent> getProblematicEvents() {
        return fProblematicEvents;
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
     * Handle the current event
     *
     * @param event
     *            The current event
     * @param testMap
     *            The transitions of the pattern
     */
    public void handleEvent(ITmfEvent event, Map<String, TmfXmlTransitionValidator> testMap) {
        setEventConsumed(false);
        // We don't know yet if the event is coherent so we need to check the coherency and we assert it is for now
        setEventCoherent(true);
        setCoherenceCheckingNeeded(true);
        boolean isValidInput = handleActiveScenarios(event, testMap);
        /* At this point, isEventCoherent returns false if a) in at least one active scenario there was a possible transition from a state
         * which was not the current state, and b) no active scenario contained a transition from its current state.
         * We check only active scenarios because we don't want to consider entries of initial states as possible transition */
        handlePendingScenario(event, isValidInput);
        if (!isEventCoherent()) {
            // Temporary display of incoherent events (FIXME)
            System.out.println("[FSM " + fId + "] " + event.getName() + " is problematic at " + event.getTimestamp().toString());
            fProblematicEvents.add(event);
        }
    }

    /**
     * Process the active scenario with the ongoing event
     *
     * @param event
     *            The ongoing event
     * @param testMap
     *            The map of transition
     * @return True if the ongoing event validates the preconditions, false otherwise
     */
    protected boolean handleActiveScenarios(ITmfEvent event, Map<String, TmfXmlTransitionValidator> testMap) {
        if (!validatePreconditions(event, testMap)) {
            return false;
        }

        // The event is valid, we can handle the active scenario
        for (Iterator<TmfXmlScenario> currentItr = fActiveScenariosList.iterator(); currentItr.hasNext();) {
            TmfXmlScenario scenario = currentItr.next();
            // Remove inactive scenarios or handle the active ones.
            if (!scenario.isActive()) {
                currentItr.remove();
            } else {
                handleScenario(scenario, event, isCoherenceCheckingNeeded());
                if (fConsuming && isEventConsumed()) {
                    return true;
                }
            }
        }
        // The event is valid but hasn't been consumed. We return true.
        return true;
    }

    /**
     * Handle the pending scenario.
     *
     * @param event
     *            The ongoing event
     * @param isInputValid
     *            Either the ongoing event validated the preconditions or not
     */
    private void handlePendingScenario(ITmfEvent event, boolean isInputValid) {
        if (fConsuming && isEventConsumed()) {
            return;
        }

        TmfXmlScenario scenario = fPendingScenario;
        if ((fInitialStateId.equals(TmfXmlState.INITIAL_STATE_ID) || isInputValid) && scenario != null) {
            handleScenario(scenario, event, isEventCoherent());
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
        for (TmfXmlScenario scenario : fActiveScenariosList) {
            if (scenario.isActive()) {
                scenario.cancel();
            }
        }
    }

    protected static void handleScenario(TmfXmlScenario scenario, ITmfEvent event, boolean isCoherenceCheckingNeeded) {
        if (scenario.isActive() || scenario.isPending()) {
            scenario.handleEvent(event, isCoherenceCheckingNeeded);
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
    public synchronized void createScenario(@Nullable ITmfEvent event, TmfXmlPatternEventHandler eventHandler, boolean force) {
        if (force || isNewScenarioAllowed()) {
            // fPendingScenario = new TmfXmlScenario(event, eventHandler, fId, fContainer, fModelFactory);
            fPendingScenario = new TmfXmlScenarioObserver(event, eventHandler, fId, fContainer, fModelFactory);
            fTotalScenarios++;
        }
    }

    /**
     * Add a scenario to the active scenario list
     *
     * @param scenario
     *            The scenario
     */
    private void addActiveScenario(TmfXmlScenario scenario) {
        fActiveScenariosList.add(scenario);
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
}