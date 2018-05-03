/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlTransitionValidator;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.FsmStateIncoherence;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlScenarioObserver;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * State system analysis for pattern matching analysis described in XML. This
 * module will parse the XML description of the analyses and execute it against
 * the trace and will execute all required action
 *
 * @author Jean-Christian Kouame
 */
public class XmlPatternStateSystemModule extends TmfStateSystemAnalysisModule {

    private @Nullable Path fXmlFile;
    private final ISegmentListener fListener;
    private @Nullable XmlPatternStateProvider fStateProvider;
    
    private String fAlgoId;
    private final boolean fForceObservation;

    List<TmfInferredEvent> fInferredEvents;
    boolean hasMultiInferredEvent;

    /**
     * Constructor
     *
     * @param listener
     *            Listener for segments that will be created
     * @param forceObservation 
     */
    public XmlPatternStateSystemModule(ISegmentListener listener, boolean forceObservation) {
        super();
        fListener = listener;
        fStateProvider = null;
        fAlgoId = TmfXmlScenarioObserver.ALGO1; // by default, use this coherence algorithm
        fForceObservation = forceObservation;
        fInferredEvents = null;
        hasMultiInferredEvent = false;
    }

    @Override
    protected @NonNull ITmfStateProvider createStateProvider() {
        String id = getId();
        fStateProvider = new XmlPatternStateProvider(checkNotNull(getTrace()), id, fXmlFile, fListener, fForceObservation);
        if (fAlgoId == null) {
        	fStateProvider.setNoObservers();
        }
        else {
	        for (TmfXmlFsm fsm : fStateProvider.getEventHandler().getFsmMap().values()) {
	    		fsm.setCoherenceAlgorithm(fAlgoId);
	    	}
        }
        return fStateProvider;
    }

    /**
     * Sets the file path of the XML file containing the state provider
     *
     * @param file
     *            The full path to the XML file
     */
    public void setXmlFile(Path file) {
        fXmlFile = file;
    }

    public XmlPatternStateProvider getStateProvider() {
        return fStateProvider;
    }
    
    /**
     * Select a new algorithm for the coherence checking instead of the default one
     * It should be called before the start of event handling 
     * @param algoId
     * 			The id of the algorithm to use
     */
    public void changeCoherenceAlgorithm(String algoId) {
    	fAlgoId = algoId;
    }

    /**
     * Get the inferred events computed after the analysis,
     * sorted by their timestamp
     *
     * @return
     * 			The list of inferred events
     */
	public List<TmfInferredEvent> getInferredEvents() {
		if (fInferredEvents == null) {
			fInferredEvents = new ArrayList<>();
			/* Wait for the analysis to complete */
			waitForCompletion();
			/* Compute the inferences */
			TmfXmlPatternEventHandler handler = getStateProvider().getEventHandler();
			handler.computeInferences();
			/* Create the inferred events */
			ITmfTrace trace = getTrace();
			Map<String, TmfXmlTransitionValidator> testMap = handler.getTestMap();
			for (TmfXmlFsm fsm : handler.getFsmMap().values()) { // we need to get the incoherences of each FSM
				for (FsmStateIncoherence incoherence : fsm.getIncoherences()) {
					long index = 1;
					Map<TmfXmlFsmTransition, TmfInferredEvent> localEventsMap = new HashMap<>(); // inferred events related to the current incoherence
					List<TmfXmlFsmTransition> transitions = incoherence.getInferredTransitions();
					Iterator<TmfXmlFsmTransition> it = transitions.iterator();
					int nbInferred = transitions.size() - 1 ; // (nb transitions - 1) because no inferred event for the last transition
					while (it.hasNext()) {
						TmfXmlFsmTransition inferredTransition = it.next();
						if (it.hasNext()) { // it means this is not the last transition, whose label is the incoherent event
							TmfInferredEvent inferredEvent = TmfInferredEvent.create(
									trace,
									incoherence,
									inferredTransition,
									index,
									nbInferred,
									testMap,
									getStateSystem(),
									fsm.getActiveScenariosList().get(incoherence.getScenarioAttribute()).getScenarioInfos());
							fInferredEvents.add(inferredEvent);
							localEventsMap.put(inferredTransition, inferredEvent);
							index++;
							
							if (inferredEvent.isMulti()) {
								hasMultiInferredEvent = true;
							}
						}
					}
					
//					incoherence.setInferences(localEventsMap);
				}
			}
			/* Sort the list of inferred events before saving it */
			fInferredEvents.sort(new Comparator<TmfInferredEvent>() {
				@Override
				public int compare(TmfInferredEvent event1, TmfInferredEvent event2) {
					if (event1.equals(event2)) {
						return 0;
					}
					return event1.greaterThan(event2) ? 1 : -1;
				}
			});
		}
		return fInferredEvents;
	}
	
    public boolean hasMultiInferredEvents() {
    	return hasMultiInferredEvent;
    }
    
    public List<TmfInferredEvent> getMultiInferredEvents() {
    	List<TmfInferredEvent> multiInferredEvents = new ArrayList<>();
    	for (TmfInferredEvent inferredEvent : fInferredEvents) {
    		if (inferredEvent.isMulti()) {
    			multiInferredEvents.add(inferredEvent);
    		}
    	}
		return multiInferredEvents;
    }

}
