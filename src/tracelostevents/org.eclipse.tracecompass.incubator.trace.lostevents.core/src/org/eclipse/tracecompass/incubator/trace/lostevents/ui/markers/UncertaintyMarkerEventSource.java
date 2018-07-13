package org.eclipse.tracecompass.incubator.trace.lostevents.ui.markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenario;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateSystemModule;
import org.eclipse.tracecompass.incubator.coherence.module.TmfAnalysisModuleHelperXml;
import org.eclipse.tracecompass.incubator.internal.trace.lostevents.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEventSource;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import com.google.common.collect.Multimap;

/**
 * IMarkerEventSource implementation for Certainty Markers, which highlight areas on entries
 * where the coherence of the state is uncertain
 * These markers are trace-specific to the LostEventsTrace type
 *
 * @author mmartin
 *
 */
public class UncertaintyMarkerEventSource implements IMarkerEventSource {

	public static final RGBA CERTAINTY_COLOR = new RGBA(0, 0, 0, 50);
	public static String FSM_ANALYSIS_ID = "kernel.linux.pattern.from.fsm";

	private @NonNull ITmfTrace fTrace;
	private  List<IMarkerEvent> fMarkers = Collections.synchronizedList(new ArrayList<>());
	private long[] fLastRequest;
	XmlPatternStateSystemModule fModule = null;


	/**
	 * Constructor
	 * @param trace
	 *         The trace associated with the trace-specific markers that will be created
	 */
	public UncertaintyMarkerEventSource(ITmfTrace trace) {
		fTrace = trace;
	}

	/**
	 * Create one category per FSM, using the ids provided
	 * by the analysis module
	 */
	@Override
	public List<@NonNull String> getMarkerCategories() {
	    List<@NonNull String> fsmIds = new ArrayList<>();
	    if (fModule == null) {
	        getModule();
	    }
	    if (fModule != null) { // the view is open
    	    TmfXmlPatternEventHandler handler = fModule.getStateProvider().getEventHandler();
            if (handler != null) {
    	        for (TmfXmlFsm fsm  : handler.getFsmMap().values()) {
    	            if (!fsm.getId().equals(TmfXmlPatternEventHandler.FSM_PROCESS_ID)) { // these markers are view-specific
    	                fsmIds.add(fsm.getId());
    	            }
    	        }
            }
	    }
        return fsmIds;
	}

	@Override
	public List<@NonNull IMarkerEvent> getMarkerList(String category, long startTime, long endTime, long resolution,
			IProgressMonitor monitor) {

		ITmfStateSystem ss = getStateSystem();
        if (ss == null) {
        	return Collections.emptyList();
        }

        long[] request = new long[] { startTime, endTime, resolution, ss.getCurrentEndTime() };
        if (Arrays.equals(request, fLastRequest)) {
            return fMarkers;
        }

        int startingNodeQuark;
        try {
        	startingNodeQuark = ss.getQuarkAbsolute(TmfXmlStrings.SCENARIOS);
        } catch (AttributeNotFoundException e) {
        	startingNodeQuark = -1;
        }
	    if (startingNodeQuark == -1) {
	    	return Collections.emptyList();
	    }

	    if (fModule == null) {
	    	return Collections.emptyList();
	    }

	    List<Integer> fsmQuarks = ss.getQuarks(startingNodeQuark, category); // get the quark of the FSM designated by the category string
	    for (Integer fsmQuark : fsmQuarks) {
	        List<Integer> quarks = ss.getQuarks(fsmQuark, "*"); // get every scenario quark
	    	for (Integer scenarioQuark : quarks) {
	    	    // Check if scenario is active
                try {
                    ITmfStateInterval stateInterval = ss.querySingleState(ss.getCurrentEndTime() - 1, ss.getQuarkRelative(scenarioQuark, TmfXmlStrings.STATE));
    	    	    String value = (String) stateInterval.getValue();
    	    	    if (value == null || value.equals(TmfXmlState.INITIAL_STATE_ID)) {
    	    	        continue;
    	    	    }
                } catch (StateSystemDisposedException | AttributeNotFoundException e2) {
                    // TODO Auto-generated catch block
                    e2.printStackTrace();
                }
	    		int quark;
				try {
					quark = ss.getQuarkRelative(scenarioQuark, TmfXmlScenarioHistoryBuilder.CERTAINTY_STATUS); // get the certainty attribute quark
				} catch (AttributeNotFoundException e1) {
					quark = -1;
				}
				if (quark == -1) {
			    	continue;
			    }

	    		int attributeQuark;
				try {
					attributeQuark = ss.getQuarkRelative(scenarioQuark, TmfXmlScenario.ATTRIBUTE_PATH); // get the "scenario attribute" attribute quark
				} catch (AttributeNotFoundException e1) {
					attributeQuark = -1;
				}
				if (attributeQuark == -1) {
			    	continue;
			    }

			    try {
		            long start = Math.max(startTime, ss.getStartTime());
		            long end = Math.min(endTime, ss.getCurrentEndTime());
		            if (start <= end) {
		                /* Update start to ensure that the previous marker is included. */
		                start = Math.max(start - 1, ss.getStartTime());
		                /* Update end to ensure that the next marker is included. */
		                long nextStartTime = ss.querySingleState(end, quark).getEndTime() + 1;
		                end = Math.min(nextStartTime, ss.getCurrentEndTime());
		                List<ITmfStateInterval> intervals = StateSystemUtils.queryHistoryRange(ss, quark, start, end, resolution, monitor);
		                for (ITmfStateInterval interval : intervals) {
		                    if (interval.getStateValue().isNull()) {
		                        continue;
		                    }

		                    long intervalStartTime = interval.getStartTime();
		                    long duration = interval.getEndTime() - intervalStartTime;
		                    // Display a marker only if the certainty status is uncertain
		                    if (interval.getStateValue().unboxStr().equals(TmfXmlScenarioHistoryBuilder.UNCERTAIN)) {
		                        IMarkerEvent uncertainZone = new MarkerEvent(null, intervalStartTime, duration, category, CERTAINTY_COLOR, null, true);
		                        if (!fMarkers.contains(uncertainZone)) {
		                        	fMarkers.add(uncertainZone);
		                        }
		                    }
		                }
		            }
		        } catch (AttributeNotFoundException | StateSystemDisposedException e) {
		            /* ignored */
		        }
	    	}
	    }

	    return fMarkers;
	}

	private void getModule() {
	    if (fModule != null) {
	        return;
	    }
	    XmlPatternAnalysis moduleParent = TmfTraceUtils.getAnalysisModuleOfClass(fTrace, XmlPatternAnalysis.class, FSM_ANALYSIS_ID);
        if (moduleParent == null) {
            Multimap<String, IAnalysisModuleHelper> helpers = TmfAnalysisManager.getAnalysisModules();
            for (IAnalysisModuleHelper helper : helpers.values()) {
                if (helper.appliesToTraceType(fTrace.getClass())) {
                    if (TmfAnalysisModuleHelperXml.class.isAssignableFrom(helper.getClass())) {
                        try {
                            if (FSM_ANALYSIS_ID.equals(helper.getId())) {
                                IAnalysisModule module = helper.newModule(fTrace);
                                moduleParent = (XmlPatternAnalysis) module;
                                break;
                            }
                        } catch (TmfAnalysisException e) {
                            Activator.getInstance().logWarning("Error creating analysis module", e);
                        }
                    }
                }
            }
        }

        if (moduleParent == null) {
            return;
        }

        moduleParent.schedule();
        moduleParent.waitForCompletion();

        fModule = moduleParent.getStateSystemModule();
	}

	private ITmfStateSystem getStateSystem() {
        getModule();
        if (fModule == null) {
            return null;
        }

        if (fModule.getTrace() != this.fTrace) { // the view's trace has not been updated yet
            return null;
        }

        fModule.waitForCompletion();
        return fModule.getStateSystem();
    }

}
