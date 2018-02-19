package org.eclipse.tracecompass.incubator.coherence.ui.views;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.incubator.coherence.core.trace.InferenceTrace;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

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
	
	public GlobalInferenceView() {
		super();
	}
	
	@Override
	public void dispose() {
		/* We need to close the trace, otherwise it will stay open forever */
		ITmfTrace trace = getTrace();
		broadcast(new TmfTraceClosedSignal(this, trace));
		trace.dispose();
		// manually set the new current trace in TmfTraceManager ? because not reset after traceClosed has been called... FIXME ?
		broadcast(new TmfTraceSelectedSignal(this, TmfTraceManager.getInstance().getOpenedTraces().iterator().next()));
	    super.dispose();
	}
	
	/**
	 * Assign a trace to this view.
	 *  
	 * @param newTrace
	 * 			The trace that should be displayed. It must be of type InferenceTrace.
	 */
	public void setProperties(InferenceTrace newTrace) {
		/* Note that we don't want to broadcast the signal because this trace should only be 
		   visible in this view */
		TmfTraceOpenedSignal signal = new TmfTraceOpenedSignal(this, newTrace, null);
		newTrace.traceOpened(signal);
		TmfTraceManager.getInstance().traceOpened(signal); // create a trace context for this trace
		traceSelected(new TmfTraceSelectedSignal(this, newTrace)); // select the trace for this view
		refresh();
	}

	/**
	 * Run the kernel analysis again and refresh the view with the new results.
	 * We need this to be able to update the view when new values are selected.
	 * 
	 * TODO maybe specifiy a reduced interval in need of an update (timestamp of the inferred event +/- 1)
	 */
	public void needRefresh() {
		// Run the analysis
		KernelAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(getTrace(), KernelAnalysisModule.class, KernelAnalysisModule.ID);
		module.resetAnalysis();
		TmfTraceManager.deleteSupplementaryFiles(getTrace()); // TODO delete only one
		module.schedule();
		module.waitForCompletion();
		// Update the view
		rebuild();
		refresh();		
	}
}
