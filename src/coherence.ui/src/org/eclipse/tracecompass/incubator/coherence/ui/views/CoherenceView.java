package org.eclipse.tracecompass.incubator.coherence.ui.views;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.FsmStateIncoherence;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateProvider;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateSystemModule;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.incubator.coherence.ui.widgets.CoherenceTooltipHandler;
import org.eclipse.tracecompass.incubator.internal.coherence.ui.views.CoherencePresentationProvider;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.interval.TmfStateInterval;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphPresentationProvider2;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import com.google.common.collect.Multimap;
import org.eclipse.tracecompass.incubator.coherence.module.TmfAnalysisModuleHelperXml;


public class CoherenceView extends ControlFlowView {

	private final List<IMarkerEvent> fMarkers = Collections.synchronizedList(new ArrayList<>());
	private final List<ITmfEvent> fEvents = Collections.synchronizedList(new ArrayList<>()); // list of incoherent events

	public String COHERENCE_LABEL = "Incoherent";
	public String COHERENCE = "Coherence warning";
	private static final RGBA COHERENCE_COLOR = new RGBA(255, 0, 0, 50);
	
	public static String ID = "org.eclipse.tracecompass.incubator.coherence.ui.view"; 

	public static String FSM_ANALYSIS_ID = "kernel.linux.pattern.from.fsm";
	
	// TODO: should be removed when incubator analysis xml and tmf ones are merged
	Map<ITmfTrace, IAnalysisModule> fModules = new HashMap<>(); // pair of (trace, incubator analysis xml module)
	
	private CoherenceTooltipHandler fCoherenceToolTipHandler;
	private List<FsmStateIncoherence> fIncoherences = new ArrayList<>();
	
	private Map<String, Set<FsmStateIncoherence>> pEntries = Collections.synchronizedMap(new HashMap<>()); // pair of (entry id/scenario attribute, set of associated incoherent events)
	
	private final TimeGraphPresentationProvider fNewPresentation; // replace fPresentation from ControlFlowView
	
	private Job fJob;
	
	XmlPatternStateSystemModule fModule;
	
	private Map<ITmfStateInterval, TmfXmlFsmTransition> transitionsMap = new HashMap<>(); // needed to set the transition of IncoherentEvent

	public CoherenceView() {
	    super();
	    
	    fNewPresentation = new CoherencePresentationProvider();
	}
	
	@Override
	public ControlFlowEntry findEntry(@NonNull ITmfTrace trace, int tid, long time) {
		return super.findEntry(trace, tid, time);
	}

	public XmlPatternStateSystemModule getModule() {
		return fModule;
	}
	
	/**
	 * Override this method in order to return our custom presentation provider
	 */
	@Override
	protected ITimeGraphPresentationProvider2 getPresentationProvider() {
        return fNewPresentation;
    }

	@Override
	public void dispose() {
		if (fJob != null) {
			fJob.cancel();
			fJob = null;
		}
	    for (IAnalysisModule module : fModules.values()) {
	    	((XmlPatternAnalysis) module).dispose(); // this will dispose the sub-analyses
		}
	    super.dispose();
	}

	@Override
    @TmfSignalHandler
	public void traceSelected(@Nullable TmfTraceSelectedSignal signal) {
		// Make sure we don't request data twice for the same trace
		if (getTrace() != signal.getTrace()) {
			super.traceSelected(signal);
		    Job job = fJob;
		    if (job != null) { // a job is already running => cancel it
	            job.cancel();
	        }		
			job = new Job("(trace selected) CoherenceView request data " + getTrace().getPath()) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						fEvents.clear();
						fMarkers.clear();
						fIncoherences.clear();
						pEntries.clear();
						transitionsMap.clear();
						return requestData(monitor);
					} finally {
						fJob = null;
					}
				}
			};
			fJob = job;
			job.schedule();
			return;
		}
		super.traceSelected(signal);
	}

	@TmfSignalHandler
    @Override
    public void traceOpened(@Nullable TmfTraceOpenedSignal signal) {
        super.traceOpened(signal);
	    
        Job job = fJob;
	    if (job != null) { // a job is already running => cancel it
            job.cancel();
        }		
		job = new Job("(trace opened) CoherenceView request data " + getTrace().getPath()) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					fEvents.clear();
					fMarkers.clear();
					fIncoherences.clear();
					pEntries.clear();
					transitionsMap.clear();
					return requestData(monitor);
				} finally {
					fJob = null;
				}
			}
		};
		fJob = job;
		job.schedule();
    }

	@TmfSignalHandler
    @Override
    public void traceClosed(@Nullable TmfTraceClosedSignal signal) {
		if (fJob != null) {
            fJob.cancel();
            fJob = null;
        }	
        super.traceClosed(signal);
        fEvents.clear();
        fMarkers.clear();
        transitionsMap.clear();
    }
	
    /**
     * Get the analysis module required for this view, or create it if it does not exist yet
     * 
     * Note: we need this method because TmfTrace.getAnalysisModulesOfClass(..) calls the method
     * TmfAnalysisManager.getAnalysisModules which returns a map from the multimap of all modules
     * and we have two analysis with the same id, one from tmf.analysis.xml.core, the other from
     * incubator.analysis.core
     * 
     * @param trace
     * @param helperClass
     * @param id
     * @return
     */
    public IAnalysisModule findModule(ITmfTrace trace, Class<? extends IAnalysisModuleHelper> helperClass, String id) {
    	if (!fModules.containsKey(trace)) {
	    	Multimap<String, IAnalysisModuleHelper> helpers = TmfAnalysisManager.getAnalysisModules();
	    	for (IAnalysisModuleHelper helper : helpers.values()) {
	    		if (helper.appliesToTraceType(trace.getClass())) {
	    			if (helperClass.isAssignableFrom(helper.getClass())) {
	    				try {
				    		IAnalysisModule module = helper.newModule(trace);
					    	if (id.equals(module.getId())) {
					    		fModules.put(trace, module);
					    	}
					    	else {
					    		module.dispose();
					    	}

	    	            } catch (TmfAnalysisException e) {
	    	                Activator.logWarning("Error creating analysis module", e);
	    	            }
	    			}
		    	}
			}
    	}
    	return fModules.get(trace);
    }

	/**
	 * Run the XML analysis and collect the state machines from the analysis
	 * @return 
	 */
	public @NonNull IStatus requestData(IProgressMonitor monitor) {
		ITmfTrace trace = checkNotNull(getTrace());
		
		if (monitor.isCanceled()) {
    		return Status.CANCEL_STATUS;
    	}
	    
		IAnalysisModule moduleParent = findModule(trace, TmfAnalysisModuleHelperXml.class, FSM_ANALYSIS_ID);
		if (moduleParent == null || monitor.isCanceled()) {
			return Status.CANCEL_STATUS;
		}

	    moduleParent.schedule();
	    moduleParent.waitForCompletion(monitor);
	    

	    fModule = ((XmlPatternAnalysis) moduleParent).getStateSystemModule();

        XmlPatternStateProvider provider = fModule.getStateProvider();
        if (provider == null || monitor.isCanceled()) {
        	return Status.CANCEL_STATUS;
        }

        TmfXmlPatternEventHandler handler = provider.getEventHandler();
        if (handler == null || monitor.isCanceled()) {
            return Status.CANCEL_STATUS;
        }

        Map<String, TmfXmlFsm> fsmMap = handler.getFsmMap();

        if (fsmMap.isEmpty() || monitor.isCanceled()) {
            return Status.CANCEL_STATUS;
        }
        
        for (TmfXmlFsm fsm : fsmMap.values()) {
        	if (monitor.isCanceled()) {
        		return Status.CANCEL_STATUS;
        	}
        	
            List<ITmfEvent> events = fsm.getIncoherentEvents();
            fEvents.addAll(events);
            
            fIncoherences.addAll(fsm.getIncoherences());
        }

        for (FsmStateIncoherence incoherence : fIncoherences) {
    		if (monitor.isCanceled()) {
        		return Status.CANCEL_STATUS;
        	}
            // Add the incoherent event to the set of the corresponding entry
            String tidStr = incoherence.getScenarioAttribute();
            Set<FsmStateIncoherence> eventSet;
            if (pEntries.containsKey(tidStr)) {
            	eventSet = pEntries.get(tidStr);
    		}
    		else {
    			eventSet = new HashSet<>();
    		}
			eventSet.add(incoherence);
			pEntries.put(tidStr, eventSet);
        }
        
        refresh();
        return Status.OK_STATUS;
	}

	@Override
	protected @NonNull List<String> getViewMarkerCategories() {
	    return Arrays.asList(COHERENCE);

	}

	@Override
	protected List<IMarkerEvent> getViewMarkerList(long startTime, long endTime,
	        long resolution, @NonNull IProgressMonitor monitor) {

		/* Coherence markers */
		
		for (FsmStateIncoherence incoherence : fIncoherences) {
			ITmfEvent event = incoherence.getIncoherentEvent();
			// Add incoherent marker
            long eventTime = event.getTimestamp().getValue();
            if (eventTime >= startTime && eventTime <= endTime) {
            	// marker by entry
            	int tid =  Integer.valueOf(incoherence.getScenarioAttribute());
            	ControlFlowEntry entry = this.findEntry(getTrace(), tid, event.getTimestamp().getValue());
            	
                IMarkerEvent markerByEntry = new MarkerEvent(entry, eventTime, 1, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
                IMarkerEvent marker = new MarkerEvent(null, eventTime, 1, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
                if (!fMarkers.contains(markerByEntry)) {
                    fMarkers.add(marker);
                    fMarkers.add(markerByEntry);
                }
            }
		}
		
        return fMarkers;
	}
	
	/**
	 * Create a "sub" time event, to complete the interval state in case of a time event ending in the middle of the interval range
	 * @param interval
	 * @param controlFlowEntry
	 * @param start
	 * @param duration
	 * @return
	 * 			The newly created time event
	 */
	private ITimeEvent createSubEvent(ITmfStateInterval interval, ControlFlowEntry controlFlowEntry, long start, long duration) {
		Object status = interval.getValue();
        if (status instanceof Integer) {
        	int statusValue = (int) status;
        	TimeEvent subEvent = new TimeEvent(controlFlowEntry, start, duration, statusValue);
        	return subEvent;
        }
        else {
        	NullTimeEvent subEvent = new NullTimeEvent(controlFlowEntry, start, duration);
        	return subEvent;
        }  
	}
	
	/**
	 * 
	 * @param ts
	 * @param ss
	 * @param certaintyStatusQuark
	 * @return
	 * 			True by default (when the value is not in the state system), the state value otherwise
	 */
	private boolean isCertainState(long ts, ITmfStateSystem ss, int certaintyStatusQuark) {
		if (certaintyStatusQuark == -1) {
			return true;
		}
		else {
			String certaintyValue;
			try {
				certaintyValue = ss.querySingleState(ts, certaintyStatusQuark).getStateValue().unboxStr();
			} catch (StateSystemDisposedException e) {
				return true;
			}
			
			return certaintyValue.equals(TmfXmlScenarioHistoryBuilder.CERTAIN);
		}
	}
	
	/**
	 * Create the list of time events for this entry
	 * Add the incoherent events if necessary
	 * 
	 * @see ControlFlowView.createTimeEvents 
	 */
	@Override
	protected List<ITimeEvent> createTimeEvents(ControlFlowEntry controlFlowEntry, Collection<ITmfStateInterval> value) {
		try {
			if (fJob != null) { // a job is being run
				fJob.join(); // wait for the end of requestData
			}
		} catch (InterruptedException e) {
			Activator.getDefault().logError("The job was interrupted", e);
			return Collections.emptyList();
		}
		
		// Get the quark for the certainty status of this entry in the state system
		ITmfStateSystem ss = fModule.getStateSystem();
		int certaintyStatusQuark;
		try {
			certaintyStatusQuark = ss.getQuarkAbsolute("scenarios", "process_fsm", String.valueOf(controlFlowEntry.getThreadId()), TmfXmlScenarioHistoryBuilder.CERTAINTY_STATUS); //FIXME : more general than just process_fsm
		} catch (AttributeNotFoundException e) {
			certaintyStatusQuark = -1;
		}
		
		// Get iterator on incoherent events
		Set<FsmStateIncoherence> eventSet = pEntries.get(String.valueOf(controlFlowEntry.getThreadId()));
		if (eventSet != null) {
			Iterator<FsmStateIncoherence> incoherentEventsIt = eventSet.iterator(); // select events for this entry
	        FsmStateIncoherence incoherentEvent = null;
	        long incoherentEventTs = 0;
	        TmfXmlFsmTransition transition = null;
	        if (incoherentEventsIt.hasNext()) {
	        	incoherentEvent = incoherentEventsIt.next();
	        	incoherentEventTs = incoherentEvent.getIncoherentEvent().getTimestamp().getValue();
	        	transition = incoherentEvent.getInferredTransitions().get(incoherentEvent.getInferredTransitions().size() - 1); // get last transition
	        }
		
			// Add incoherent state intervals to the given list of intervals
			Collection<ITmfStateInterval> newValue = new ArrayList<>();
			
			for (ITmfStateInterval interval : value) {
				// Case 1: the incoherent event is at the start of the next interval (end of the current interval + 1)
				if ((incoherentEvent != null) 
						&& ((incoherentEventTs == (interval.getEndTime() + 1)))) {
					ITmfStateInterval newInterval;
					newInterval = new TmfStateInterval(
							interval.getStartTime(), 
							interval.getEndTime(), 
							interval.getAttribute(), 
							IncoherentEvent.INCOHERENT_VALUE);
					transitionsMap.put(newInterval, transition);
				
					if (!isCertainState(newInterval.getStartTime(), ss, certaintyStatusQuark)) {
						// TODO select another prev state because this one is not certain
						Activator.getDefault().logWarning("Previous event coherence is uncertain");
					}
					
					newValue.add(newInterval);
					// Get the next incoherent event, if it exists
	        		if (incoherentEventsIt.hasNext()) {
	        			incoherentEvent = incoherentEventsIt.next();
	        			incoherentEventTs = incoherentEvent.getIncoherentEvent().getTimestamp().getValue();
	    	        	transition = incoherentEvent.getInferredTransitions().get(incoherentEvent.getInferredTransitions().size() - 1); // get last transition
	        		}
	        		else {
	        			incoherentEvent = null;
	        			incoherentEventTs = 0;
	        		}
					
				}
				// Case 2: the incoherent event is in the middle of the current interval
				else if ((incoherentEvent != null) 
						&& ((incoherentEventTs > interval.getStartTime()) 
								&& (incoherentEventTs < interval.getEndTime()))) {
					ITmfStateInterval newInterval1 = new TmfStateInterval(
							interval.getStartTime(), 
							incoherentEventTs - 1, 
							interval.getAttribute(), 
							IncoherentEvent.INCOHERENT_VALUE);
					transitionsMap.put(newInterval1, transition);
					ITmfStateInterval newInterval2 = new TmfStateInterval(
							incoherentEventTs, 
							interval.getEndTime(), 
							interval.getAttribute(), 
							interval.getValue());
					
					if (!isCertainState(newInterval1.getStartTime(), ss, certaintyStatusQuark)) {
						// TODO select another prev state because this one is not certain
						Activator.getDefault().logWarning("Previous event coherence is uncertain");
					}
					
					newValue.add(newInterval1);
					newValue.add(newInterval2);
					// Get the next incoherent event, if it exists
	        		if (incoherentEventsIt.hasNext()) {
	        			incoherentEvent = incoherentEventsIt.next();
	        			incoherentEventTs = incoherentEvent.getIncoherentEvent().getTimestamp().getValue();
	    	        	transition = incoherentEvent.getInferredTransitions().get(incoherentEvent.getInferredTransitions().size() - 1); // get last transition
	        		}
	        		else {
	        			incoherentEvent = null;
	        			incoherentEventTs = 0;
	        			transition = null;
	        		}
				}
				// Default case
				else {
					newValue.add(interval);
				}
			}
			
			/* Create the time events from the new list of intervals
			 * 
			 * This is a copy of @see ControlFlowView.createTimeEvents
			 * but with createTimeEventCustom used (which cannot be overriden because static)
			 * It could be replaced by a simple call to the parent method once the ControlFlowView method is rewritten
			 * to support the new mechanism of creating IncoherentEvent 
			 */
			List<ITimeEvent> events = new ArrayList<>(newValue.size());
	        ITimeEvent prev = null;
	        for (ITmfStateInterval interval : newValue) {
	            ITimeEvent event = createTimeEventCustom(interval, controlFlowEntry);
	            if (prev != null) {
	                long prevEnd = prev.getTime() + prev.getDuration();
	                if (prevEnd < event.getTime()) {
	                    // fill in the gap.
	                    events.add(new TimeEvent(controlFlowEntry, prevEnd, event.getTime() - prevEnd));
	                }
	            }
	            prev = event;
	            events.add(event);
	        }
	        return events;
		}
		else {
			return super.createTimeEvents(controlFlowEntry, value);
		}
    }
	
	protected TimeEvent createTimeEventCustom(ITmfStateInterval interval, ControlFlowEntry controlFlowEntry) {
        long startTime = interval.getStartTime();
        long duration = interval.getEndTime() - startTime + 1;
        Object status = interval.getValue();
        if (status instanceof Integer) {
        	if (((int) status) == IncoherentEvent.INCOHERENT_VALUE) {
        		// Find the transition
        		TmfXmlFsmTransition transition = transitionsMap.get(interval);
        		return new IncoherentEvent(controlFlowEntry, startTime, duration, transition);
        	}
            return new TimeEvent(controlFlowEntry, startTime, duration, (int) status);
        }
        return new NullTimeEvent(controlFlowEntry, startTime, duration);
    }	
	
	@Override
    public void createPartControl(Composite parent) {
        super.createPartControl(parent);
        
        // Change the presentation provider to the custom one
        this.getTimeGraphViewer().setTimeGraphProvider(fNewPresentation);
        
        // TODO Deactivate old tooltip
        getTimeGraphViewer().getTimeGraphControl();
        
        // Activate new tooltip
        IStatusLineManager statusManager = getViewSite().getActionBars().getStatusLineManager();
        fCoherenceToolTipHandler = new CoherenceTooltipHandler(
        		this.getPresentationProvider(), 
        		getTimeGraphViewer().getTimeGraphScale().getTimeProvider(), 
        		statusManager); // FIXME we should not access the statusLineManager this way, but through Control
		fCoherenceToolTipHandler.activateHoverHelp(getTimeGraphViewer().getTimeGraphControl());
	}
}
