package org.eclipse.tracecompass.incubator.coherence.ui.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.incubator.coherence.core.trace.InferenceTrace;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * A view to show the consequences of the inferred events on the 
 * control flow view 
 * 
 * @author mmartin
 *
 * Note: we don't need to override createTimeEvents, because the TestTrace 
 * is used in the analysis
 */
public class GlobalInferenceView extends ControlFlowView {

	public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.coherence.ui.views.global.inference";
	private static final String INFERENCE = "Inferred event";
	private static final String INFERENCE_LABEL = "Inferred";
	private static final RGBA INFERENCE_COLOR = new RGBA(255, 0, 0, 50);
	
	private final List<IMarkerEvent> fMarkers = Collections.synchronizedList(new ArrayList<>());
	
	public GlobalInferenceView() {
		super();
	}
	
	@Override
	public void dispose() {
		fMarkers.clear();
		/* We need to close the trace, otherwise it will stay open forever */
		ITmfTrace trace = getTrace();
		if (trace instanceof InferenceTrace) { // we need this test because sometimes this view is open with a real trace and we don't want to close the trace in this case  
			broadcast(new TmfTraceClosedSignal(this, trace));
			trace.dispose();
			// manually set the new current trace in TmfTraceManager ? because not reset after traceClosed has been called... FIXME ?
			broadcast(new TmfTraceSelectedSignal(this, TmfTraceManager.getInstance().getOpenedTraces().iterator().next()));
		}
	    super.dispose();
	}
	
	/**
	 * Assign a trace to this view.
	 *  
	 * @param newTrace
	 * 			The trace that should be displayed. It must be of type InferenceTrace.
	 */
	public void setProperties(InferenceTrace newTrace) {
		fMarkers.clear();
		/* Note that we don't want to broadcast the signal because this trace should only be 
		   visible in this view */
		TmfTraceOpenedSignal signal = new TmfTraceOpenedSignal(this, newTrace, null);
		newTrace.traceOpened(signal);
		TmfTraceManager.getInstance().traceOpened(signal); // create a trace context for this trace
		traceSelected(new TmfTraceSelectedSignal(this, newTrace)); // select the trace for this view
		/* Synchronize traces */
		TmfTraceManager.getInstance().updateTraceContext(NonNullUtils.checkNotNull(getTrace()),
				builder -> builder.setSynchronized(true));
		TmfTraceManager.getInstance().updateTraceContext(NonNullUtils.checkNotNull(((InferenceTrace) getTrace()).getParentTrace()),
				builder -> builder.setSynchronized(true));
		refresh();
	}
	
	@Override
    protected void fillLocalToolBar(IToolBarManager manager) {
        // add "Select inferences" button to local tool bar of Coherence view
		final IWorkbench wb = PlatformUI.getWorkbench();
        final IWorkbenchPage activePage = wb.getActiveWorkbenchWindow().getActivePage();
    	IViewPart view = activePage.findView(CoherenceView.ID);
    	if (view != null && view instanceof CoherenceView) {
	        IAction inferenceSelectionAction = ((CoherenceView) view).getInferenceSelectionAction();
	        manager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, inferenceSelectionAction);
	        
	        // add a separator to local tool bar
	        manager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, new Separator());
    	}
        

        super.fillLocalToolBar(manager);
    }
	
	@Override
	protected @NonNull List<String> getViewMarkerCategories() {
	    return Arrays.asList(INFERENCE);

	}

	@Override
	protected List<IMarkerEvent> getViewMarkerList(long startTime, long endTime,
	        long resolution, @NonNull IProgressMonitor monitor) {
				
		if (getTrace() instanceof InferenceTrace) {
			/* Inference markers */
			List<TmfInferredEvent> inferredEvents = ((InferenceTrace) getTrace()).getInferredEvents();
			
			for (TmfInferredEvent event : inferredEvents) {
				// Add incoherent marker
	            long eventTime = event.getTimestamp().getValue();
	            if (eventTime >= startTime && eventTime <= endTime) {
					IMarkerEvent marker = new MarkerEvent(null, eventTime, 1, INFERENCE, INFERENCE_COLOR, INFERENCE_LABEL, true);
					
					if (!fMarkers.contains(marker)) {
						fMarkers.add(marker);
					}            	
	            }
			}
			
	        return fMarkers;
		}
		else {
			return Collections.emptyList();
		}
	}
	
}
