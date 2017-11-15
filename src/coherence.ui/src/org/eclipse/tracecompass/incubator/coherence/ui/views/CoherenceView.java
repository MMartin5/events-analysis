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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenario;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder;
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
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.util.Pair;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphPresentationProvider2;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl;
import com.google.common.collect.Multimap;
import org.eclipse.tracecompass.incubator.coherence.module.TmfAnalysisModuleHelperXml;


public class CoherenceView extends ControlFlowView {

	private final List<IMarkerEvent> fMarkers = Collections.synchronizedList(new ArrayList<>());
	private final List<ITmfEvent> fEvents = Collections.synchronizedList(new ArrayList<>()); // list of incoherent events

	public String COHERENCE_LABEL = "Incoherent";
	public String COHERENCE = "Coherence warning";
	public String CERTAINTY_LABEL = "Uncertain";
	public String CERTAINTY = "Uncertain state";
	private static final RGBA COHERENCE_COLOR = new RGBA(255, 0, 0, 50);
	private static final RGBA CERTAINTY_COLOR = new RGBA(0, 0, 0, 50); 

	private String FSM_ANALYSIS_ID = "kernel.linux.pattern.from.fsm";
	
	// TODO: should be removed when incubator analysis xml and tmf ones are merged
	Map<ITmfTrace, IAnalysisModule> fModules = new HashMap<>(); // pair of (trace, incubator analysis xml module)
	
	private CoherenceTooltipHandler fCoherenceToolTipHandler;
	private Map<ITmfEvent, List<Pair<String, TmfXmlFsmTransition>>> pEventsWithTransitions = Collections.synchronizedMap(new HashMap<>());
	
	private Map<String, Set<ITmfEvent>> pEntries = Collections.synchronizedMap(new HashMap<>()); // pair of (entry id/scenario attribute, set of associated incoherent events)
	
	private final TimeGraphPresentationProvider fNewPresentation; // replace fPresentation from ControlFlowView
	
	private Job fJob;
	
	XmlPatternStateSystemModule fModule;

	public CoherenceView() {
	    super();
	    
	    fNewPresentation = new CoherencePresentationProvider();
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
						pEventsWithTransitions.clear();
						pEntries.clear();
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
					pEventsWithTransitions.clear();
					pEntries.clear();
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
        	
            List<ITmfEvent> events = fsm.getProblematicEvents();
            fEvents.addAll(events);
            
            IStatus ret = fsm.setTransitions(monitor);
            if (ret != Status.OK_STATUS) {
            	return ret;
            }
            pEventsWithTransitions.putAll(fsm.getProblematicEventsWithTransitions());
        }

        for (ITmfEvent event : fEvents) {
        	for (Pair<String, TmfXmlFsmTransition> p : pEventsWithTransitions.get(event)) {
        		if (monitor.isCanceled()) {
            		return Status.CANCEL_STATUS;
            	}
	            // Add the incoherent event to the set of the corresponding entry
	            String tidStr = p.getFirst();
	            Set<ITmfEvent> eventSet;
	            if (pEntries.containsKey(tidStr)) {
	            	eventSet = pEntries.get(tidStr);
	    		}
	    		else {
	    			eventSet = new HashSet<>();
	    		}
    			eventSet.add(event);
    			pEntries.put(tidStr, eventSet);
        	}
        }
        
        refresh();
        return Status.OK_STATUS;
	}

	@Override
	protected @NonNull List<String> getViewMarkerCategories() {
	    return Arrays.asList(COHERENCE, CERTAINTY);

	}

	@Override
	protected List<IMarkerEvent> getViewMarkerList(long startTime, long endTime,
	        long resolution, @NonNull IProgressMonitor monitor) {

		/* Coherence markers */
		
        for (ITmfEvent event : fEvents) {
            // Add incoherent marker
            long eventTime = event.getTimestamp().getValue();
            if (eventTime >= startTime && eventTime <= endTime) {
            	for (Pair<String, TmfXmlFsmTransition> p : pEventsWithTransitions.get(event)) {
                	// marker by entry
                	int tid =  Integer.valueOf(p.getFirst());
                	ControlFlowEntry entry = this.findEntry(getTrace(), tid, event.getTimestamp().getValue());
                	
                    IMarkerEvent markerByEntry = new MarkerEvent(entry, eventTime, 1, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
                    IMarkerEvent marker = new MarkerEvent(null, eventTime, 1, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
                    if (!fMarkers.contains(markerByEntry)) {
                        fMarkers.add(marker);
                        fMarkers.add(markerByEntry);
                    }
            	}
            }
        }
	    
	    /* Certainty markers */
        
        ITmfStateSystem ss = fModule.getStateSystem(); // get the state system of this analysis
        
        if (ss == null) {
        	return fMarkers;
        }
        
        int startingNodeQuark;
        try {
        	startingNodeQuark = ss.getQuarkAbsolute("scenarios"); 
        } catch (AttributeNotFoundException e) {
        	startingNodeQuark = -1;
        }
	    if (startingNodeQuark == -1) {
	    	return fMarkers;
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
		                    	ControlFlowEntry threadEntry = this.findEntry(getTrace(), tid, intervalStartTime);
		                    	IMarkerEvent uncertainZone = new MarkerEvent(threadEntry, intervalStartTime, duration, CERTAINTY, CERTAINTY_COLOR, CERTAINTY_LABEL, true);
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
		
		List<ITimeEvent> events = new ArrayList<>(value.size());
		int lastIdx = -1;
		
		// Get iterator on incoherent events
		Set<ITmfEvent> eventSet = pEntries.get(String.valueOf(controlFlowEntry.getThreadId()));
		if (eventSet != null) {
			Iterator<ITmfEvent> incoherentEventsIt = eventSet.iterator(); // select events for this entry
	        ITmfEvent incoherentEvent = null;
	        if (incoherentEventsIt.hasNext()) {
	        	incoherentEvent = incoherentEventsIt.next();
	        }
	    
	        ITimeEvent prev = null;
	        for (ITmfStateInterval interval : value) {
	        	// Make sure we are not going to overwrite the IncoherentEvent state if it is overlapping the current interval
	        	if ((prev != null) && (interval.getStartTime() < (prev.getTime() + prev.getDuration()))) { // it means the incoherent event is overstepping this interval
	        		long duration = interval.getEndTime() - interval.getStartTime() + 1;
	        		long prevDurationFromInterval = prev.getDuration() - (interval.getStartTime() - prev.getTime());
	                // Add the end of the interval as a sub TimeEvent if necessary
	                if (duration > prevDurationFromInterval) {
	                	ITimeEvent subEvent = createSubEvent(interval, controlFlowEntry, interval.getStartTime() + prevDurationFromInterval, duration - prevDurationFromInterval);
                    	events.add(subEvent);
                    	lastIdx++;
                    	prev = subEvent;                   
	                }
	        	}
	        	else {
	        		/* We have an incoherent event waiting to be set 
	        		 * AND the incoherent state (from previous coherent event to incoherent event) begins at the start of this interval
	        		 */
		        	if ((incoherentEvent != null) && ((incoherentEvent.getTimestamp().getValue() == interval.getStartTime()))) {
		        		long lastEventTs;
		        		if (lastIdx != -1) {
		        			lastEventTs = events.remove(lastIdx).getTime();
		        		}
		        		else {
		        			lastEventTs = getTrace().getStartTime().getValue(); // trace beginning
		        		}
		        		// Add the incoherent time event
		        		long incoherentDuration = incoherentEvent.getTimestamp().getValue() - lastEventTs;
		        		TmfXmlFsmTransition transition = null;
		        		for(Pair<String, TmfXmlFsmTransition> p : pEventsWithTransitions.get(incoherentEvent)) {
		        			if (p.getFirst().equals(String.valueOf(controlFlowEntry.getThreadId()))) {
		        				transition = p.getSecond();
		        				break;
		        			}
		        		}
		        		
		        		if (transition == null) {
		        			Activator.logError("Problem finding the transition associated to this event.");
		        			break;
		        		}
		                
		                IncoherentEvent newIncoherent = new IncoherentEvent(controlFlowEntry, lastEventTs, incoherentDuration, transition);
		                
		                // No need to test if we should fill in the gap because incoherent event will fill up to the last known event
		                
				        prev = newIncoherent;
		                events.add(newIncoherent);
		                // We don't increase lastIdx here because we popped the last event 
		                                
		                long incoherentEnd = newIncoherent.getTime() + newIncoherent.getDuration();
		                /* Add the end of the interval as a sub TimeEvent if necessary
		                 * We don't want to use the mechanism to "fill in the gap" because we know what this state is supposed to be
		                 * (value in the interval) whereas when we fill in the gap, we create an "unknown state" time event
		                 */
		                if (interval.getEndTime() > incoherentEnd) { // true if the incoherent event does not last until the end of the current interval
		                	long duration = interval.getEndTime() - incoherentEnd + 1;
		                	ITimeEvent subEvent = createSubEvent(interval, controlFlowEntry, incoherentEnd, duration);
	                    	events.add(subEvent);
	                    	lastIdx++;
	                    	prev = subEvent;                    
		                }
		        		
		        		// Get the next incoherent event, if it exists
		        		if (incoherentEventsIt.hasNext()) {
		        			incoherentEvent = incoherentEventsIt.next();
		        		}
		        		else {
		        			incoherentEvent = null;
		        		}
		        	}
		        	/* We have an incoherent event waiting to be set 
	        		 * AND the incoherent state is in the middle of this interval (the start time and end time are somewhere inside this interval)
	        		 */
		        	else if ((incoherentEvent != null) && ((incoherentEvent.getTimestamp().getValue() > interval.getStartTime()) && (incoherentEvent.getTimestamp().getValue() < interval.getEndTime()))) {
		        		// Add the incoherent time event
		        		long incoherentDuration = incoherentEvent.getTimestamp().getValue() - interval.getStartTime();
		        		TmfXmlFsmTransition transition = null;
		        		for(Pair<String, TmfXmlFsmTransition> p : pEventsWithTransitions.get(incoherentEvent)) {
		        			if (p.getFirst().equals(String.valueOf(controlFlowEntry.getThreadId()))) {
		        				transition = p.getSecond();
		        				break;
		        			}
		        		}
		        		
		        		if (transition == null) {
		        			Activator.logError("Problem finding the transition associated to this event.");
		        			break;
		        		}
		                
		                IncoherentEvent newIncoherent = new IncoherentEvent(controlFlowEntry, interval.getStartTime(), incoherentDuration, transition);
		                
		                if (prev != null) {
				            long prevEnd = prev.getTime() + prev.getDuration();
				            if (prevEnd < newIncoherent.getTime()) {
				                // fill in the gap.
				                events.add(new TimeEvent(controlFlowEntry, prevEnd, newIncoherent.getTime() - prevEnd));
				                lastIdx++;
				            }
				        }
		                
				        prev = newIncoherent;
		                events.add(newIncoherent);
		                lastIdx++;
		                                
		                long incoherentEnd = newIncoherent.getTime() + newIncoherent.getDuration();
		                /* Add the end of the interval as a sub TimeEvent if necessary
		                 * We don't want to use the mechanism to "fill in the gap" because we know what this state is supposed to be
		                 * (value in the interval) whereas when we fill in the gap, we create an "unknown state" time event
		                 */
		                if (interval.getEndTime() > incoherentEnd) { // true if the incoherent event does not last until the end of the current interval
		                	long duration = interval.getEndTime() - incoherentEnd + 1;
		                	ITimeEvent subEvent = createSubEvent(interval, controlFlowEntry, incoherentEnd, duration);
	                    	events.add(subEvent);
	                    	lastIdx++;
	                    	prev = subEvent;                    
		                }
		        		
		        		// Get the next incoherent event, if it exists
		        		if (incoherentEventsIt.hasNext()) {
		        			incoherentEvent = incoherentEventsIt.next();
		        		}
		        		else {
		        			incoherentEvent = null;
		        		}
		        	}
		        	else {
		        		// Create a normal TimeEvent
				        ITimeEvent event = createTimeEvent(interval, controlFlowEntry);
				        if (prev != null) {
				            long prevEnd = prev.getTime() + prev.getDuration();
				            if (prevEnd < event.getTime()) {
				                // fill in the gap.
				                events.add(new TimeEvent(controlFlowEntry, prevEnd, event.getTime() - prevEnd));
				                lastIdx++;
				            }
				        }
				        prev = event;
				        events.add(event);
				        lastIdx++;
		        	}
		        }
			}
		}
		else {
			events = super.createTimeEvents(controlFlowEntry, value); // use parent's method if there are no incoherent events
		}
        return events;
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
