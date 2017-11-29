/*******************************************************************************
 * Copyright (c) 2016 Ecole Polytechnique de Montreal, Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.tracecompass.incubator.coherence.core.model;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder.ScenarioStatusType;
import org.eclipse.tracecompass.incubator.coherence.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;

/**
 * This Class implements a Scenario in the XML-defined state system
 */
public class TmfXmlScenario {

    protected final IXmlStateSystemContainer fContainer;
    protected final TmfXmlFsm fFsm;
    protected TmfXmlPatternEventHandler fPatternHandler;
    protected TmfXmlScenarioInfo fScenarioInfo;
    protected TmfXmlScenarioHistoryBuilder fHistoryBuilder;
    
    protected String fAttribute = null;
    
    public static String ATTRIBUTE_PATH = "attribute";

    /**
     * Constructor
     *
     * @param event
     *            The event at which this scenario is created
     * @param patternHandler
     *            The filter handler
     * @param fsmId
     *            the id of the fsm executed by this scenario
     * @param container
     *            The state system container this scenario belongs to
     * @param modelFactory
     *            The model factory
     */    
    public TmfXmlScenario(@Nullable ITmfEvent event, TmfXmlPatternEventHandler patternHandler, String fsmId, 
    		IXmlStateSystemContainer container, ITmfXmlModelFactory modelFactory) {
        TmfXmlFsm fsm = patternHandler.getFsm(fsmId);
        if (fsm == null) {
            throw new IllegalArgumentException(fsmId + "has not been declared."); //$NON-NLS-1$
        }
        fFsm = fsm;
        fContainer = container;
        fHistoryBuilder = ((XmlPatternStateProvider) container).getHistoryBuilder();
        fPatternHandler = patternHandler;
        int quark = fHistoryBuilder.assignScenarioQuark(fContainer, fsmId);
        int statusQuark = fHistoryBuilder.getScenarioStatusQuark(fContainer, quark);
        fScenarioInfo = new TmfXmlScenarioInfo(fFsm.getInitialStateId(), ScenarioStatusType.PENDING, quark, statusQuark, fFsm);
        fHistoryBuilder.update(fContainer, fScenarioInfo, event);
        
        if (event != null) {
        	fAttribute = setAttribute(); // attribute should be set after the fHistoryBuilder.update if there is an event
        }
    }

    /**
     * Get this scenario infos
     *
     * @return The scenario info
     */
    public TmfXmlScenarioInfo getScenarioInfos() {
        return fScenarioInfo;
    }

    /**
     * Cancel the execution of this scenario
     */
    public void cancel() {
        fScenarioInfo.setStatus(ScenarioStatusType.ABANDONED);
        if (fScenarioInfo.getStatus() != ScenarioStatusType.PENDING) {
            fHistoryBuilder.completeScenario(fContainer, fScenarioInfo, null);
        }
    }

    /**
     * Test if the scenario is active or not
     *
     * @return True if the scenario is active, false otherwise
     */
    public boolean isActive() {
        return fScenarioInfo.getStatus() == ScenarioStatusType.IN_PROGRESS;
    }

    /**
     * Test if the scenario is pending or not
     *
     * @return True if the scenario is pending, false otherwise
     */
    public boolean isPending() {
        return fScenarioInfo.getStatus() == ScenarioStatusType.PENDING;
    }

    /**
     * Handle the ongoing event
     *
     * @param event
     *            The ongoing event
     * @param isEventCoherent
     *            Value used only in TmfXmlScenarioObserver
     * @param layout 
     */
    public void handleEvent(ITmfEvent event, boolean isEventCoherent, int transitionTotal, IKernelAnalysisEventLayout layout) {

        TmfXmlStateTransition out = fFsm.next(event, fPatternHandler.getTestMap(), fScenarioInfo);
        if (out == null) {
            return;
        }

        fFsm.increaseTransitionCount();
        
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
    
    /**
     * Get the attribute uniquely identifying this scenario 
     * @return
     * 			The unique attribute
     * 
     * FIXME works only for process_fsm for now
     */
    protected String setAttribute() {
    	String value = "";
    	ITmfStateSystemBuilder ss = (ITmfStateSystemBuilder) fContainer.getStateSystem();
    	int startingNodeQuark = fScenarioInfo.getQuark();
    	String subPath = ATTRIBUTE_PATH; // we use tid to identify each scenario from "process_fsm" FSM, and cpu id for "cpu_fsm" FSM => found in "attribute" field
    	int attributeQuark;
		try {
			attributeQuark = ss.getQuarkRelative(startingNodeQuark, subPath);
		} catch (AttributeNotFoundException e) {
			System.out.println("Attribute not found.");
			return value;
		}
    	ITmfStateValue sValue = ss.queryOngoingState(attributeQuark);
//    	value = sValue.unboxStr();
    	value = String.valueOf(sValue.unboxInt());
    	return value;
    }
    
    public String getAttribute() {
    	return fAttribute;
    }

	public void updateCertainty(ITmfEvent event) {
		fHistoryBuilder.updateCertaintyStatus(fContainer, fScenarioInfo, event);
	}

}
