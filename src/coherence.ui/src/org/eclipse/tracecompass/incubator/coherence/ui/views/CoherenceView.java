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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelTidAspect;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlFsmTransition;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateProvider;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateSystemModule;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.incubator.coherence.ui.widgets.CoherenceTooltipHandler;
import org.eclipse.tracecompass.incubator.internal.coherence.ui.views.CoherencePresentationProvider;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.Activator;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.util.Pair;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphPresentationProvider2;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ILinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeLinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphColorScheme;
import com.google.common.collect.Multimap;
import org.eclipse.tracecompass.incubator.coherence.module.TmfAnalysisModuleHelperXml;

/* TODO
 * 1. récupérer fFsmMap de TmfXmlPatternEventHandler pour initialiser fFsm --> OK
 * 2. pour chaque événement incohérent des FSM, ajouter un marqueur sur la vue à cet événement --> OK
 * 3. pour chaque marqueur, chercher l'évenement précedent et marquer la zone entre ces 2 points comme incohérente
 */

public class CoherenceView extends ControlFlowView {

	private final List<IMarkerEvent> fMarkers = new ArrayList<>();
	private final Map<ITmfEvent, ITmfEvent> fEvents = new HashMap<>(); // pair of (incoherent event, previous coherent event)

	public String COHERENCE_LABEL = "Incoherent";
	public String COHERENCE = "Coherence warning";
	private static final RGBA COHERENCE_COLOR = new RGBA(255, 0, 0, 50);

	private String FSM_ANALYSIS_ID = "kernel.linux.pattern.from.fsm";
	
	// TODO: should be removed when incubator analysis xml and tmf ones are merged
	Map<ITmfTrace, IAnalysisModule> fModules = new HashMap<>(); // pair of (trace, incubator analysis xml module)
	
	private CoherenceTooltipHandler fCoherenceToolTipHandler;
	private Map<ITmfEvent, Pair<String, Set<TmfXmlFsmTransition>>> pEventsWithTransitions = new HashMap<>();
	
	CountDownLatch latch; // used to synchronize the creation of time events to the initialization of incoherent events
	
	private Map<String, Set<ITmfEvent>> pEntries = new HashMap<>(); // pair of (entry id/scenario attribute, set of associated incoherent events)
	
	private final TimeGraphPresentationProvider fNewPresentation; // replace fPresentation from ControlFlowView

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
			latch = new CountDownLatch(1);
		    super.traceSelected(signal);
		    fEvents.clear();
		    fMarkers.clear();
		    pEventsWithTransitions.clear();
		    pEntries.clear();
		    Thread thread = new Thread() {
	            @Override
	            public void run() {
	                requestData();
	            }
	        };
	        thread.start();
		}
	}

	@TmfSignalHandler
    @Override
    public void traceOpened(@Nullable TmfTraceOpenedSignal signal) {
		latch = new CountDownLatch(1);
        super.traceOpened(signal);
        fEvents.clear();
	    fMarkers.clear();
	    pEventsWithTransitions.clear();
	    pEntries.clear();
        Thread thread = new Thread() {
            @Override
            public void run() {
                requestData();
            }
        };
        thread.start();
    }

	@TmfSignalHandler
    @Override
    public void traceClosed(@Nullable TmfTraceClosedSignal signal) {
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
	 */
	public void requestData() {
	    ITmfTrace trace = checkNotNull(getTrace());
	    
		IAnalysisModule moduleParent = findModule(trace, TmfAnalysisModuleHelperXml.class, FSM_ANALYSIS_ID);
		if (moduleParent == null) {
			return;
		}

	    moduleParent.schedule();
	    moduleParent.waitForCompletion();
	    

	    XmlPatternStateSystemModule module = ((XmlPatternAnalysis) moduleParent).getStateSystemModule();

        XmlPatternStateProvider provider = module.getStateProvider();
        if (provider == null) {
            return;
        }

        TmfXmlPatternEventHandler handler = provider.getEventHandler();
        if (handler == null) {
            return;
        }

        Map<String, TmfXmlFsm> fsmMap = handler.getFsmMap();

        if (fsmMap.isEmpty()) {
            return;
        }

        List<ITmfEvent> pEvents = new ArrayList<>();
        
        for (TmfXmlFsm fsm : fsmMap.values()) {
            List<ITmfEvent> events = fsm.getProblematicEvents();
            pEvents.addAll(events);
            
            pEventsWithTransitions.putAll(fsm.getProblematicEventsWithTransitions());
            
            fsm.debugDisplayTransitionsCtr(); //FIXME
        }

        ITmfEvent traceBeginning = new TmfEvent(trace, ITmfContext.UNKNOWN_RANK , trace.getStartTime(), null, null);

        for (ITmfEvent event : pEvents) {
            // Look for events only if we didn't already find it
            if (!fEvents.containsKey(event)) {
                // Look for previous event
                List<@NonNull TimeGraphEntry> entries = getEntryList(trace);
                if (entries == null) {
                    continue;
                }
                // Get the TraceEntry, which should be the first and only entry inside the entries list
                TraceEntry traceEntry = (entries.get(0) instanceof TraceEntry) ? traceEntry = (TraceEntry) entries.get(0) : null;
                if(traceEntry == null) {
                    continue;
                }
                // Look for the right entry, according to the incoherent event tid
            	int tid =  Integer.valueOf(pEventsWithTransitions.get(event).getFirst());
            	ControlFlowEntry threadEntry = this.findEntry(getTrace(), tid, event.getTimestamp().getValue());
            	
                if (threadEntry == null) {
                    continue;
                }

                long ts = event.getTimestamp().getValue();
                ControlFlowEntry cfEntry = (ControlFlowEntry) threadEntry;
                ITmfContext ctx = trace.seekEvent(TmfTimestamp.fromNanos(ts));
                long rank = ctx.getRank();
                ctx.dispose();

                Predicate<@NonNull ITmfEvent> predicate = prevEvent -> Objects.equals(tid, KernelTidAspect.INSTANCE.resolve(prevEvent));

                // Look for the previous event in the entry
                ITmfEvent prevEvent = TmfTraceUtils.getPreviousEventMatching(cfEntry.getTrace(), rank, predicate, null);
                if (prevEvent != null) {
                    fEvents.put(event, prevEvent);
                }
                else { // TODO make sure (event == null) => beginning of the trace
                    fEvents.put(event, traceBeginning);
                }
                
                // Add the incoherent event to the set of the corresponding entry
                String tidStr = String.valueOf(tid); 
                if (pEntries.containsKey(tidStr)) {
                	Set<ITmfEvent> eventSet = pEntries.get(tidStr);
                	eventSet.add(event);
                	pEntries.replace(tidStr, eventSet);
        		}
        		else {
        			Set<ITmfEvent> newSet = new HashSet<>();
        			newSet.add(event);
        			pEntries.put(tidStr, newSet);
        		}
            }
        }
        
        latch.countDown(); // decrease the countdown
        refresh();
	}

	@Override
	protected @NonNull List<String> getViewMarkerCategories() {
	    return Arrays.asList(COHERENCE);

	}

	@Override
	protected List<IMarkerEvent> getViewMarkerList(long startTime, long endTime,
	        long resolution, @NonNull IProgressMonitor monitor) {

	    if (fMarkers.size() != fEvents.keySet().size()) { // return markers directly if we already created all of them
    	    if (fEvents.isEmpty()) {
    	        return Collections.emptyList();
    	    }

            for (ITmfEvent event : fEvents.keySet()) {
                // Add incoherent marker
                long eventTime = event.getTimestamp().getValue();
                if (eventTime >= startTime && eventTime <= endTime) {
                	// marker by entry                	
                	TmfXmlFsmTransition transition = pEventsWithTransitions.get(event).getSecond().iterator().next(); // FIXME arbitrary selection
                	int tid =  Integer.valueOf(pEventsWithTransitions.get(event).getFirst());
                	ControlFlowEntry entry = this.findEntry(getTrace(), tid, event.getTimestamp().getValue());
                	
                    IMarkerEvent markerByEntry = new MarkerEvent(entry, eventTime, 1, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
                    IMarkerEvent marker = new MarkerEvent(null, eventTime, 1, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
                    if (!fMarkers.contains(marker)) {
                        fMarkers.add(marker);
                        fMarkers.add(markerByEntry);
                    }
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
			latch.await(); // wait for the end of requestData
		} catch (InterruptedException e) {
			Activator.getDefault().logError("Cannot create time events", e);
			return Collections.emptyList();
		}
		
		List<ITimeEvent> events = new ArrayList<>(value.size());
		
		// Get iterator on incoherent events
		Set<ITmfEvent> eventSet = pEntries.get(String.valueOf(controlFlowEntry.getThreadId()));
		if (eventSet != null) {
			Iterator<ITmfEvent> incoherentEventsIt = eventSet.iterator(); // select events for this entry
	        ITmfEvent incoherentEvent = null;
	        ITmfEvent prevEvent = null;
	        if (incoherentEventsIt.hasNext()) {
	        	incoherentEvent = incoherentEventsIt.next();
	        	prevEvent = fEvents.get(incoherentEvent);
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
                    	prev = subEvent;                   
	                }
	        	}
	        	else {
	        		/* We have an incoherent event waiting to be set 
	        		 * AND (the incoherent state (from previous coherent event to incoherent event) begins at the start of this interval
	        		 * OR the incoherent state ends in between this interval (the start time of the previous coherent event is not in the interval set because of the zoom factor))
	        		 */
		        	if ((incoherentEvent != null) && ((prevEvent.getTimestamp().getValue() == interval.getStartTime()) 
		        			|| ((prevEvent.getTimestamp().getValue() < interval.getStartTime()) && (incoherentEvent.getTimestamp().getValue() > interval.getStartTime())))) {
		        		// Add the incoherent time event
		        		long incoherentDuration = incoherentEvent.getTimestamp().getValue() - prevEvent.getTimestamp().getValue();
		                Set<TmfXmlFsmTransition> transitions = pEventsWithTransitions.get(incoherentEvent).getSecond();
		                
		                IncoherentEvent newIncoherent = new IncoherentEvent(controlFlowEntry, prevEvent.getTimestamp().getValue(), incoherentDuration, transitions);
		                
		                if (prev != null) {
				            long prevEnd = prev.getTime() + prev.getDuration();
				            if (prevEnd < newIncoherent.getTime()) {
				                // fill in the gap. (between this event and the previous one)
				                events.add(new TimeEvent(controlFlowEntry, prevEnd, newIncoherent.getTime() - prevEnd));
				            }
				        }
				        prev = newIncoherent;
		                events.add(newIncoherent);
		                                
		                long incoherentEnd = newIncoherent.getTime() + newIncoherent.getDuration();
		                /* Add the end of the interval as a sub TimeEvent if necessary
		                 * We don't want to use the mechanism to "fill in the gap" because we know what this state is supposed to be
		                 * (value in the interval) whereas when we fill in the gap, we create an "unknown state" time event
		                 */
		                if (interval.getEndTime() > incoherentEnd) { // true if the incoherent event does not last until the end of the current interval
		                	long duration = interval.getEndTime() - incoherentEnd + 1;
		                	ITimeEvent subEvent = createSubEvent(interval, controlFlowEntry, incoherentEnd, duration);
	                    	events.add(subEvent);
	                    	prev = subEvent;                    
		                }
		        		
		        		// Get the next incoherent event, if it exists
		        		if (incoherentEventsIt.hasNext()) {
		        			incoherentEvent = incoherentEventsIt.next();
		        			prevEvent = fEvents.get(incoherentEvent);
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
				            }
				        }
				        prev = event;
				        events.add(event);
		        	}
		        }
			}
		}
		else {
			events = super.createTimeEvents(controlFlowEntry, value); // use parent's method if there are no incoherent events
		}
        return events;
    }

	/**
	 * Construct coherence links from the map of (incoherent, previous coherent) events
	 *
	 * @return
	 *         The list of links used by the coherence view
	 */
	public List<@NonNull ILinkEvent> getCoherenceLinks() {
		try {
			latch.await(); // wait for the end of requestData
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	    List<ILinkEvent> links = new ArrayList<>();
	    if (fEvents.isEmpty()) {
            return Collections.emptyList();
        }

	    for (ITmfEvent incoherentEvent : fEvents.keySet()) {
	        ITmfEvent prevEvent = fEvents.get(incoherentEvent);
	        if (prevEvent == null) {
	            return Collections.emptyList();
	        }
	        
	        // We need only one entry because incoherentEvent and prevEvent have same entries (same thread)
	        int tid =  Integer.valueOf(pEventsWithTransitions.get(incoherentEvent).getFirst());
        	ControlFlowEntry entry = this.findEntry(getTrace(), tid, incoherentEvent.getTimestamp().getValue());

	        // TODO draw arrows backwards
	        int value = TimeGraphColorScheme.RED_STATE;
	        links.add(new TimeLinkEvent(entry, entry, prevEvent.getTimestamp().getValue(),
	                incoherentEvent.getTimestamp().getValue() - prevEvent.getTimestamp().getValue(), value));
        }
	    return links;
	}

	@Override
	protected List<@NonNull ILinkEvent> getLinkList(long zoomStartTime, long zoomEndTime, long resolution,
            @NonNull IProgressMonitor monitor) {
		try {
			latch.await(); // wait for the end of requestData
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	    List<@NonNull ILinkEvent> linkList = new ArrayList<>();
	    // Look for "normal" links for ControlFlow view
	    linkList = super.getLinkList(zoomStartTime, zoomEndTime, resolution, monitor);
	    if (linkList == null) {
	        return Collections.emptyList();
	    }
	    // Add the coherence links
	    List<ILinkEvent> links = getCoherenceLinks();
	    if (!links.isEmpty()) {
	    	linkList.addAll(links);
	    }
	    return linkList;
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
