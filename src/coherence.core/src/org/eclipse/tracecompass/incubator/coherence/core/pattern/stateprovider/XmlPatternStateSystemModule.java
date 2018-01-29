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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlScenarioObserver;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;

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

}
