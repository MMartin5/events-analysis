package org.eclipse.tracecompass.incubator.internal.coherence.ui.actions;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.incubator.coherence.ui.Activator;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.incubator.coherence.ui.views.InferenceView;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

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
    private final ControlFlowEntry fEntry;
	private long fEntryQuark;

    /**
     * Constructor
     * 
     * @param event
     * 			The incoherent event associated with the state selected by the user
     * @param entry
     * 			The entry on the control flow view
     * @param entryQuark 
     */
    public DisplayInferenceAction(IncoherentEvent event, ControlFlowEntry entry, long entryQuark) {
        this(event, entry, entry.getName(), new HostThread(entry.getTrace().getHostId(), entry.getThreadId()), entryQuark);
    }

    /**
     * Constructor
     *
     * @param event
     * 			The incoherent event associated with the state selected by the user
     * @param entry
     * 			The entry on the control flow view
     * @param threadName
     *            the thread name, can be null
     * @param ht
     *            The HostThread to follow
     * @param entryQuark 
     */
    public DisplayInferenceAction(IncoherentEvent event, 
    		ControlFlowEntry entry,  
    		@Nullable String threadName, 
    		HostThread ht, 
    		long entryQuark) {
    	fIncoherence = event;
        fThreadName = threadName;
        fHostThread = ht;
        fEntry = entry;
        fEntryQuark = entryQuark;
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
    	/* Open the view */
    	final IWorkbench wb = PlatformUI.getWorkbench();
        final IWorkbenchPage activePage = wb.getActiveWorkbenchWindow().getActivePage();
        try {
        	// secondary id is the thread id, because there should be only one view per selected thread
        	String secondaryId = (fThreadName == null) ? fHostThread.getTid().toString() : fThreadName + '/' + fHostThread.getTid();
        	IViewPart view = activePage.showView(InferenceView.ID, secondaryId, IWorkbenchPage.VIEW_ACTIVATE);
        	if (view instanceof InferenceView) {
	        	InferenceView inferenceView = (InferenceView) view;
	        	inferenceView.setProperties(fEntry, fIncoherence, fEntryQuark);
        	}
		} catch (PartInitException e) {
			Activator.logError("Unable to open the view.", e);
		}
    	
        
        super.run();
    }
    
}
