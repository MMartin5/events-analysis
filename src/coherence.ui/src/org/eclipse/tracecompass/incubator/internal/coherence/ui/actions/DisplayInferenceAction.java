package org.eclipse.tracecompass.incubator.internal.coherence.ui.actions;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.signals.TmfThreadSelectedSignal;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.incubator.coherence.ui.views.CoherenceView;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;

/**
 * Display Inference Action
 * This action displays the inferences on the selected object
 * inspired by @see FollowThreadAction
 * 
 * @author mmartin
 *
 */
public class DisplayInferenceAction extends Action {
	private final IncoherentEvent fIncoherence;
    private final HostThread fHostThread;
    private final @Nullable String fThreadName;

    /**
     * Constructor
     *
     * @param source
     *            the view that is generating the signal, but also shall
     *            broadcast it
     * @param threadName
     *            the thread name, can be null
     * @param threadId
     *            the thread id
     * @param trace
     *            the trace containing the thread
     */
    public DisplayInferenceAction(IncoherentEvent event, @Nullable String threadName, int threadId, ITmfTrace trace) {
        this(event, threadName, new HostThread(trace.getHostId(), threadId));
    }

    /**
     * Constructor
     *
     * @param source
     *            the view that is generating the signal, but also shall broadcast
     *            it
     * @param threadName
     *            the thread name, can be null
     * @param ht
     *            The HostThread to follow
     */
    public DisplayInferenceAction(IncoherentEvent event, @Nullable String threadName, HostThread ht) {
    	fIncoherence = event;
        fThreadName = threadName;
        fHostThread = ht;
    }

	@Override
    public String getText() {
        if (fThreadName == null) {
            return Messages.DisplayInferenceAction_display + ' ' + fHostThread.getTid();
        }
        return Messages.DisplayInferenceAction_display + ' ' + fThreadName + '/' + fHostThread.getTid();
    }

    @Override
    public void run() {
    	for (TmfInferredEvent event : fIncoherence.getIncoherence().getInferredEvents()) {
    		System.out.println(event.toString());
    	}
    	System.out.println("youpi");
        super.run();
    }
}
