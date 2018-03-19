package org.eclipse.tracecompass.incubator.coherence.core.trace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.tmf.core.TmfCommonConstants;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfBaseAspects;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSelectionRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfStartAnalysisSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfWindowRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.location.ITmfLocation;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfLocation;
import org.eclipse.tracecompass.tmf.ctf.core.context.CtfTmfContext;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.event.aspect.CtfChannelAspect;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectRegistry;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceFolder;

import com.google.common.collect.ImmutableList;

/**
 * A trace that allows for the insertion of inferred events in a "real" CTF trace
 * It is synthetically created by overriding the getNext method and thus, returning 
 * inferred events when it should appear. 
 *  
 * @author mmartin
 *
 */
public class InferenceTrace extends TmfTrace implements IKernelTrace {

	private List<TmfInferredEvent> fInferredEvents;
	private Iterator<TmfInferredEvent> it;
	private TmfInferredEvent currentInferred;
	private TmfTrace fTrace;
	private IResource fResource;
	
	private static String suffix = ".2"; // FIXME : better name
	
	public InferenceTrace() {
		super();
	}
	
	public InferenceTrace(TmfTrace trace, List<TmfInferredEvent> inferredEvents) throws TmfTraceException {
		super();
		IResource original = trace.getResource();
		
		IPath newPath = new Path(original.getFullPath().toOSString() + suffix);
		try {
			// TODO maybe create new resource completely? because we have to set the supp. files manually anyway...
			IPath checkPath = new Path(original.getName() + suffix);
			IResource old = original.getParent().findMember(checkPath);
			if (old != null) {
				old.delete(true, null);
			}
			original.copy(newPath, true, null);
			IResource copy = original.getWorkspace().getRoot().getFolder(newPath);
			TmfTraceFolder newFolder = new TmfTraceFolder(original.getParent().getName(), (IFolder) copy, TmfProjectRegistry.getProject(original.getProject()));
			IFolder supplFolder = newFolder.prepareTraceSupplementaryFolder(copy.getFullPath().toOSString(), true);
			copy.setPersistentProperty(TmfCommonConstants.TRACE_SUPPLEMENTARY_FOLDER, supplFolder.getLocation().toOSString()); // force new folder for supp. files			
			initialize(copy, trace.getPath(), TmfEvent.class); // copy the path because the modules creation should depend on the resource
			TmfTraceManager.deleteSupplementaryFiles(this);
			fResource = copy;
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
		fTrace = trace;
		fInferredEvents = new ArrayList<>(inferredEvents); // inferredEvents is already sorted
		it = fInferredEvents.iterator();
		currentInferred = (it.hasNext()) ? it.next() : null;
		
        this.setStartTime(fTrace.getStartTime());
        this.setEndTime(fTrace.getEndTime());
	}
	
	@Override
	public synchronized void dispose() {
		// Delete "synthetic" eclipse resource 
		try {
			fResource.delete(true, null);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		super.dispose();
	}
	
	public TmfTrace getParentTrace() {
		return fTrace;
	}

	@Override
	public void traceRangeUpdated(TmfTraceRangeUpdatedSignal signal) {
		long start = signal.getRange().getStartTime().getValue();
		it = fInferredEvents.iterator();
		if (it.hasNext()) {
			currentInferred = it.next();
		}
		while (it.hasNext() && currentInferred.getTimestamp().getValue() < start) {
    		currentInferred = it.next();
    	}
		if (!it.hasNext() && currentInferred.getTimestamp().getValue() < start) {
			currentInferred = null;
		}
		
		super.traceRangeUpdated(signal);
	}
	
	@TmfSignalHandler
    public void selectionRangeUpdated(final TmfSelectionRangeUpdatedSignal signal) {
		long start = signal.getBeginTime().getValue();
		it = fInferredEvents.iterator();
		if (it.hasNext()) {
			currentInferred = it.next();
		}
		while (it.hasNext() && currentInferred.getTimestamp().getValue() < start) {
    		currentInferred = it.next();
    	}
		if (!it.hasNext() && currentInferred.getTimestamp().getValue() < start) {
			currentInferred = null;
		}
	}
	
	@TmfSignalHandler
    public void windowRangeUpdated(final TmfWindowRangeUpdatedSignal signal) {
		long start = signal.getCurrentRange().getStartTime().getValue();
		it = fInferredEvents.iterator();
		if (it.hasNext()) {
			currentInferred = it.next();
		}
		while (it.hasNext() && currentInferred.getTimestamp().getValue() < start) {
    		currentInferred = it.next();
    	}
		if (!it.hasNext() && currentInferred.getTimestamp().getValue() < start) {
			currentInferred = null;
		}
	}
	
	@TmfSignalHandler
    public void analysisStarted(final TmfStartAnalysisSignal signal) {
		it = fInferredEvents.iterator();
		currentInferred = it.hasNext() ? it.next() : null;
	}

	@Override
	public synchronized ITmfEvent getNext(ITmfContext context) {
		
        CtfTmfEvent event = null;
        if (context instanceof CtfTmfContext) {
            if (context.getLocation() == null || CtfLocation.INVALID_LOCATION.equals(context.getLocation().getLocationInfo())) {
                return null;
            }
            CtfTmfContext ctfContext = (CtfTmfContext) context;
            event = ctfContext.getCurrentEvent();

            if (event != null) {
            	long ts = event.getTimestamp().getValue();
                if ((currentInferred != null) &&
                		(currentInferred.getTimestamp().getValue() < ts)) {
                	
                    updateAttributes(context, currentInferred);
                    ctfContext.increaseRank();                    
                    CtfInferredEvent newEvent = new CtfInferredEvent(currentInferred, this);
                    currentInferred = (it.hasNext()) ? it.next() : null;
                    return newEvent;
            	}

                updateAttributes(context, event);
                ctfContext.advance();
                ctfContext.increaseRank();
            }
        }
        return new CtfInferredEvent(event, this);
	}

	@Override
	public IStatus validate(IProject project, String path) {
		return fTrace.validate(project, path);
	}

	@Override
	public ITmfLocation getCurrentLocation() {
		return fTrace.getCurrentLocation();
	}

	@Override
	public double getLocationRatio(ITmfLocation location) {
		return fTrace.getLocationRatio(location);
	}

	@Override
	public ITmfContext seekEvent(ITmfLocation location) {
		return fTrace.seekEvent(location);
	}

	@Override
	public ITmfContext seekEvent(double ratio) {
		return fTrace.seekEvent(ratio);
	}

	@Override
	public ITmfEvent parseEvent(ITmfContext context) {
		return fTrace.parseEvent(context);
	}

	@Override
	public IKernelAnalysisEventLayout getKernelEventLayout() {
		return ((IKernelTrace) fTrace).getKernelEventLayout();
	}
	
	@Override
    public boolean matches(ITmfEvent event) {
        if (event.getTrace() == this) {
            return true;
        }
        if (event.getTrace() == fTrace) {
        	return true;
        }
        return false;
    }
	
	// copy from @see CtfTmfTrace
	protected static final @NonNull Collection<@NonNull ITmfEventAspect<?>> CTF_ASPECTS = ImmutableList.of(
            TmfBaseAspects.getTimestampAspect(),
            new CtfChannelAspect(),
            new MyCpuAspect(),
            TmfBaseAspects.getEventTypeAspect(),
            TmfBaseAspects.getContentsAspect());
	
	@Override
    public Iterable<ITmfEventAspect<?>> getEventAspects() {
        return CTF_ASPECTS;
    }
}
