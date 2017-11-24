package org.eclipse.tracecompass.incubator.trace.lostevents.ui.markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenario;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateSystemModule;
import org.eclipse.tracecompass.incubator.coherence.ui.views.CoherenceView;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEventSource;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * IMarkerEventSource implementation for Certainty Markers, which highlight areas on entries
 * where the coherence of the state is uncertain
 * These markers are trace-specific to the LostEventsTrace type
 *
 * @author mmartin
 *
 */
public class UncertaintyMarkerEventSource implements IMarkerEventSource {

	private String CERTAINTY = "Uncertain state"; //$NON-NLS-1$
	private static final RGBA CERTAINTY_COLOR = new RGBA(0, 0, 0, 50);

	private @NonNull ITmfTrace fTrace;
	private  List<IMarkerEvent> fMarkers = Collections.synchronizedList(new ArrayList<>());
	private long[] fLastRequest;
	CoherenceView fView = null;


	/**
	 * Constructor
	 * @param trace
	 *         The trace associated with the trace-specific markers that will be created
	 */
	public UncertaintyMarkerEventSource(ITmfTrace trace) {
		fTrace = trace;
	}

	@Override
	public List<@NonNull String> getMarkerCategories() {
		return Arrays.asList(CERTAINTY);
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
        	startingNodeQuark = ss.getQuarkAbsolute("scenarios");
        } catch (AttributeNotFoundException e) {
        	startingNodeQuark = -1;
        }
	    if (startingNodeQuark == -1) {
	    	return Collections.emptyList();
	    }

	    if (fView == null) {
	    	return Collections.emptyList();
	    }

	    List<Integer> fsmQuarks = ss.getQuarks(startingNodeQuark, "*"); // get every FSM quark
	    for (Integer fsmQuark : fsmQuarks) {
				if (!ss.getAttributeName(fsmQuark).equals("process_fsm")) { // FIXME: allow temporarily only for process_fsm
					continue;
				}
	        List<Integer> quarks = ss.getQuarks(fsmQuark, "*"); // get every scenario quark
	    	for (Integer scenarioQuark : quarks) {
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
		                    	int tid = ss.querySingleState(start, attributeQuark).getStateValue().unboxInt(); // the scenario tid is the entry tid
		                    	if (tid == -1) {
		                    		continue;
		                    	}
		                    	ControlFlowEntry threadEntry = fView.findEntry(fTrace, tid, intervalStartTime);
		                    	IMarkerEvent uncertainZone = new MarkerEvent(threadEntry, intervalStartTime, duration, CERTAINTY, CERTAINTY_COLOR, null, true);
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

	private void getView() {
	    Display display = Display.getDefault();
	    if (display != null) {
    	    display.syncExec(new Runnable() {
    	        @Override
                public void run() {
            		final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
                    fView = (CoherenceView) page.findView(CoherenceView.ID);
    	        }

    	    });
	    }
	}

	private ITmfStateSystem getStateSystem() {
        getView();
        if (fView == null) {
            return null;
        }

        XmlPatternStateSystemModule module = fView.getModule();
        if (module == null) {
            return null;
        }

        return module.getStateSystem();
    }

}