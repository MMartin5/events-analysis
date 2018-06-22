package org.eclipse.tracecompass.incubator.coherence.ui.views;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenario;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.FsmStateIncoherence;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateProvider;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateSystemModule;
import org.eclipse.tracecompass.incubator.coherence.core.trace.InferenceTrace;
import org.eclipse.tracecompass.incubator.coherence.module.TmfAnalysisModuleHelperXml;
import org.eclipse.tracecompass.incubator.coherence.ui.Activator;
import org.eclipse.tracecompass.incubator.coherence.ui.dialogs.InferenceDialog;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.incubator.coherence.ui.widgets.CoherenceTooltipHandler;
import org.eclipse.tracecompass.incubator.internal.coherence.ui.actions.DisplayInferenceAction;
import org.eclipse.tracecompass.incubator.internal.coherence.ui.views.CoherencePresentationProvider;
import org.eclipse.tracecompass.incubator.trace.lostevents.ui.markers.UncertaintyMarkerEventSource;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphPresentationProvider2;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphViewer;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ILinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NamedTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeLinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;


public class CoherenceView extends ControlFlowView {

	private final List<IMarkerEvent> fMarkers = Collections.synchronizedList(new ArrayList<>());

	public String COHERENCE_LABEL = "Incoherent";
	public String COHERENCE = "Coherence warning";
	private static final RGBA COHERENCE_COLOR = new RGBA(255, 0, 0, 50);
	
	public static String ID = "org.eclipse.tracecompass.incubator.coherence.ui.view";
		
	private CoherenceTooltipHandler fCoherenceToolTipHandler;
	private List<FsmStateIncoherence> fIncoherences = new ArrayList<>();
	
	private Map<String, Set<FsmStateIncoherence>> pEntries = Collections.synchronizedMap(new HashMap<>()); // pair of (entry id/scenario attribute, set of associated incoherent events)
	
	private final TimeGraphPresentationProvider fNewPresentation; // replace fPresentation from ControlFlowView
	
	private Job fJob;
	
	// TODO: should be removed when incubator analysis xml and tmf ones are merged
	Map<ITmfTrace, IAnalysisModule> fModules = new HashMap<>(); // pair of (trace, incubator analysis xml module)
	XmlPatternStateSystemModule fModule = null;
	
	private Map<ITimeGraphState, FsmStateIncoherence> incoherencesMap = new HashMap<>(); // used to instantiate IncoherentEvent
	
	private final @NonNull MenuManager fEventMenuManager = new MenuManager();
	
	private Map<String, TmfXmlScenario> scenarios = new HashMap<>();
	
	private Action fInferenceSelectionAction;
	private Action fGlobalInferenceViewAction;
	private static final String ICON_PATH = "icons/licorne.gif"; //$NON-NLS-1$
	// FIXME messages
	private static final String TOOLTIP_TEXT = "Select Values for Inferences"; //$NON-NLS-1$
	private static final String LABEL_TEXT = "Infer"; //$NON-NLS-1$
	private static final String ICON_PATH2 = "icons/path3699.png"; //$NON-NLS-1$
	// FIXME messages
	private static final String TOOLTIP_TEXT2 = "Open Global Inference View"; //$NON-NLS-1$
	private static final String LABEL_TEXT2 = "Open"; //$NON-NLS-1$
	
	Map<ITmfTrace, InferenceDialog> dialogs;

	private Map<Long, ITmfEvent> fIncoherentEvents = new HashMap<>();

	public CoherenceView() {
	    super();
	    
	    fNewPresentation = new CoherencePresentationProvider();
	    dialogs = new HashMap<>();
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
		if (fModule != null) {
			fModule.dispose();
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
						fMarkers.clear();
						fIncoherences.clear();
						fIncoherentEvents.clear();
						pEntries.clear();
						incoherencesMap.clear();
						scenarios.clear();
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
					fMarkers.clear();
					fIncoherences.clear();
					fIncoherentEvents.clear();
					pEntries.clear();
					incoherencesMap.clear();
					scenarios.clear();
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
	    if (getTrace() == signal.getTrace()) {
    		if (fJob != null) {
                fJob.cancel();
                fJob = null;
            }	
            super.traceClosed(signal);
            fMarkers.clear();
            fIncoherences.clear();
			fIncoherentEvents.clear();
			pEntries.clear();
            incoherencesMap.clear();
            scenarios.clear();
	    }
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
	    
		XmlPatternAnalysis moduleParent = TmfTraceUtils.getAnalysisModuleOfClass(trace, XmlPatternAnalysis.class, UncertaintyMarkerEventSource.FSM_ANALYSIS_ID);
		if (moduleParent == null) {
			if( monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
			moduleParent = (XmlPatternAnalysis) fModules.get(trace);
			if (moduleParent == null) {
				Multimap<String, IAnalysisModuleHelper> helpers = TmfAnalysisManager.getAnalysisModules();
					for (IAnalysisModuleHelper helper : helpers.values()) {
						if (helper.appliesToTraceType(trace.getClass())) {
							if (TmfAnalysisModuleHelperXml.class.isAssignableFrom(helper.getClass())) {
								try {
									if (UncertaintyMarkerEventSource.FSM_ANALYSIS_ID.equals(helper.getId())) {
										IAnalysisModule module = helper.newModule(trace);
										fModules.put(trace, module);
										moduleParent = (XmlPatternAnalysis) module;
										break;
									}
								} catch (TmfAnalysisException e) {
									Activator.logWarning("Error creating analysis module", e);
								}
							}
						}
					}
			}

		}
		
		if (moduleParent == null) {
			return Status.CANCEL_STATUS;
		}

	    moduleParent.schedule();
	    moduleParent.waitForCompletion(monitor);
	    

	    if (fModule != null) {
	    	fModule.dispose();
	    }
	    fModule = moduleParent.getStateSystemModule();

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
            
            fIncoherences.addAll(fsm.getIncoherences());
            
            scenarios.putAll(fsm.getActiveScenariosList());
        }
        
        // Sort incoherences in chronological order
        fIncoherences.sort(new Comparator<FsmStateIncoherence>() {

			@Override
			public int compare(FsmStateIncoherence inc1, FsmStateIncoherence inc2) {
				if (inc1.getIncoherentEvent().getTimestamp().getValue() < inc2.getIncoherentEvent().getTimestamp().getValue()) {
					return -1;
				}
				else if (inc1.getIncoherentEvent().getTimestamp().getValue() > inc2.getIncoherentEvent().getTimestamp().getValue()) {
					return 1;
				}
				else {
					return 0;
				}
			}
        	
		});

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
    			eventSet = new LinkedHashSet<>();
    		}
			eventSet.add(incoherence);
			pEntries.put(tidStr, eventSet);
			
			fIncoherentEvents.put(incoherence.getIncoherentEvent().getTimestamp().getValue(), incoherence.getIncoherentEvent());
			
        }
        
        refresh();
        return Status.OK_STATUS;
	}

	@Override
	protected @NonNull List<String> getViewMarkerCategories() {
	    return Arrays.asList(COHERENCE, TmfXmlPatternEventHandler.FSM_PROCESS_ID);

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
				ControlFlowEntry entry = findEntry(getEntryQuarkFromTid(tid)); // TODO we should only look for the entry if the incoherence is related to a process (process_fsm)
				IMarkerEvent markerByEntry = new MarkerEvent(entry, eventTime, 0, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
				// simple marker
				IMarkerEvent marker = new MarkerEvent(null, eventTime, 0, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
				
				if (!fMarkers.contains(markerByEntry)) {
					fMarkers.add(marker);
					fMarkers.add(markerByEntry);
				}            	
            }
		}
		
		/* Uncertainty process markers */
		
		if (fModule != null) {
			ITmfStateSystem ss = fModule.getStateSystem();
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
	
		    List<Integer> fsmQuarks = ss.getQuarks(startingNodeQuark, TmfXmlPatternEventHandler.FSM_PROCESS_ID); // get the quark of the FSM designated by the category string
		    for (Integer fsmQuark : fsmQuarks) {
		        List<Integer> quarks = ss.getQuarks(fsmQuark, "*"); // get every scenario quark
		    	for (Integer scenarioQuark : quarks) {
		    		// Check if scenario is active
	                try {
	                    ITmfStateInterval stateInterval = ss.querySingleState(getTrace().getEndTime().getValue() - 2, ss.getQuarkRelative(scenarioQuark, TmfXmlStrings.STATE));
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
			                        /* Note that we query at 'end - 1' because the attribute could have not been set yet at 'start'
			                           and all fields are reset at end, so add a -1 offset */
			                        int tid = ss.querySingleState(end - 2, attributeQuark).getStateValue().unboxInt(); // the scenario tid is the entry tid
			                    	if (tid == -1) {
			                    		continue;
			                    	}
			                    	long entryQuark = getEntryQuarkFromTid(tid);
			                    	if (entryQuark == -1) { // no entry with this tid exists in the view, don't add a marker 
			                    		continue;
			                    	}
			                    	ControlFlowEntry threadEntry = findEntry(entryQuark);
			                    	IMarkerEvent uncertainZone = new MarkerEvent(threadEntry, intervalStartTime, duration, TmfXmlPatternEventHandler.FSM_PROCESS_ID, UncertaintyMarkerEventSource.CERTAINTY_COLOR, null, true);
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
	    }
		
        return fMarkers;
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
	 * @see BaseDataProviderTimeGraphView.createTimeEvents 
	 */
	@Override
	protected List<ITimeEvent> createTimeEvents(TimeGraphEntry entry, List<ITimeGraphState> values) {
		ControlFlowEntry controlFlowEntry = (ControlFlowEntry) entry; 
		/* Ignore swappers */
		if (controlFlowEntry.getThreadId() == 0) {
			return Collections.emptyList();
		}
		
		try {
			if (fJob != null) { // a job is being run
				fJob.join(); // wait for the end of requestData
			}
		} catch (InterruptedException e) {
			Activator.getDefault();
			Activator.logError("The job was interrupted", e);
			return Collections.emptyList();
		}
		
		// Get the quark for the certainty status of this entry in the state system
		if (fModule == null) {
			return Collections.emptyList();
		}
		ITmfStateSystem ss = fModule.getStateSystem();
		int certaintyStatusQuark;
		try {
			TmfXmlScenario scenario = scenarios.get(String.valueOf(controlFlowEntry.getThreadId()));
			if (scenario == null) {
				return Collections.emptyList();
			}
			int scenarioQuark = scenario.getScenarioInfos().getQuark();
			certaintyStatusQuark = ss.getQuarkRelative(scenarioQuark, TmfXmlScenarioHistoryBuilder.CERTAINTY_STATUS);
		} catch (AttributeNotFoundException e) {
			certaintyStatusQuark = -1;
		}
		
		// Get iterator on incoherent events
		Set<FsmStateIncoherence> eventSet = pEntries.get(String.valueOf(controlFlowEntry.getThreadId()));
		if (eventSet != null) {
			Iterator<FsmStateIncoherence> incoherentEventsIt = eventSet.iterator(); // select events for this entry
	        FsmStateIncoherence incoherentEvent = null;
	        long incoherentEventTs = 0;
	        if (incoherentEventsIt.hasNext()) {
	        	incoherentEvent = incoherentEventsIt.next();
	        	incoherentEventTs = incoherentEvent.getIncoherentEvent().getTimestamp().getValue();
	        }
	        ITimeGraphState firstInterval = values.get(0);
	        while (incoherentEventsIt.hasNext() && incoherentEventTs < firstInterval.getStartTime()) {
	        	incoherentEvent = incoherentEventsIt.next();
	        	incoherentEventTs = incoherentEvent.getIncoherentEvent().getTimestamp().getValue();
	        }
		
			// Add incoherent state intervals to the given list of intervals
	        List<ITimeGraphState> newValue = new ArrayList<>();
			
	        Iterator<ITimeGraphState> it = values.iterator();
	        ITimeGraphState interval = null;
	        boolean takeNext = true;
			while (it.hasNext() || !takeNext) { // Make sure we don't erase the last state if there is no next interval but a waiting interval 
				if (takeNext) {
					interval = it.next();
				}
				// Case 1: the incoherent event is at the start of the next interval (end of the current interval)
				if ((incoherentEvent != null) 
						&& ((incoherentEventTs == (interval.getStartTime() + interval.getDuration())))) {
					ITimeGraphState newInterval;
					newInterval = new TimeGraphState(interval.getStartTime(), interval.getDuration(), IncoherentEvent.INCOHERENT_VALUE, interval.getLabel());
					incoherencesMap.put(newInterval, incoherentEvent);
				
					if (!isCertainState(newInterval.getStartTime(), ss, certaintyStatusQuark)) {
						// TODO select another prev state because this one is not certain
						Activator.logWarning("Previous event coherence is uncertain");
					}
					
					newValue.add(newInterval);
					takeNext = true;
					// Get the next incoherent event, if it exists
	        		if (incoherentEventsIt.hasNext()) {
	        			incoherentEvent = incoherentEventsIt.next();
	        			incoherentEventTs = incoherentEvent.getIncoherentEvent().getTimestamp().getValue();
	        		}
	        		else {
	        			incoherentEvent = null;
	        			incoherentEventTs = 0;
	        		}
					
				}
				// Case 2: the incoherent event is in the middle of the current interval
				else if ((incoherentEvent != null) 
						&& ((incoherentEventTs > interval.getStartTime()) 
								&& (incoherentEventTs < interval.getStartTime() + interval.getDuration()))) {
					ITimeGraphState newInterval1 = new TimeGraphState(interval.getStartTime(), incoherentEventTs - interval.getStartTime(), IncoherentEvent.INCOHERENT_VALUE, interval.getLabel());
					incoherencesMap.put(newInterval1, incoherentEvent);
					ITimeGraphState newInterval2 = new TimeGraphState(incoherentEventTs, interval.getDuration() - newInterval1.getDuration(), interval.getValue(), interval.getLabel());
					
					if (!isCertainState(newInterval1.getStartTime(), ss, certaintyStatusQuark)) {
						Activator.getDefault();
						// TODO select another prev state because this one is not certain
						Activator.logWarning("Previous event coherence is uncertain");
					}
					
					newValue.add(newInterval1);
					// Don't add newInterval2 now, because there might be another incoherent event in the middle of this interval
					takeNext = false;
					interval = newInterval2;
//					newValue.add(newInterval2);
					// Get the next incoherent event, if it exists
	        		if (incoherentEventsIt.hasNext()) {
	        			incoherentEvent = incoherentEventsIt.next();
	        			incoherentEventTs = incoherentEvent.getIncoherentEvent().getTimestamp().getValue();
	        		}
	        		else {
	        			incoherentEvent = null;
	        			incoherentEventTs = 0;
	        		}
				}
				// Default case
				else {
					newValue.add(interval);
					takeNext = true;
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
	        for (ITimeGraphState newInterval : newValue) {
	            ITimeEvent event = createTimeEventCustom(newInterval, controlFlowEntry);
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
			return simpleCreateTimeEvents(controlFlowEntry, values);
		}
    }
	
	/* copy from @see BaseDataProviderTimeGraphView.createTimeEvents */
	private List<ITimeEvent> simpleCreateTimeEvents(TimeGraphEntry entry, List<ITimeGraphState> values) {
        List<ITimeEvent> events = new ArrayList<>(values.size());
        ITimeEvent prev = null;
        for (ITimeGraphState state : values) {
            ITimeEvent event = createTimeEvent(entry, state);
            if (prev != null) {
                long prevEnd = prev.getTime() + prev.getDuration();
                if (prevEnd < event.getTime()) {
                    // fill in the gap.
                    events.add(new TimeEvent(entry, prevEnd, event.getTime() - prevEnd));
                }
            }
            prev = event;
            events.add(event);
        }
        return events;
    }
	
	protected TimeEvent createTimeEventCustom(ITimeGraphState interval, ControlFlowEntry controlFlowEntry) {
        long startTime = interval.getStartTime();
        long duration = interval.getDuration();
        long status = interval.getValue();
        if (status == Integer.MIN_VALUE) {
        	return new NullTimeEvent(controlFlowEntry, startTime, duration);
        }
        if (((int) status) == IncoherentEvent.INCOHERENT_VALUE) {
    		// Find the transition
    		FsmStateIncoherence incoherence = incoherencesMap.get(interval);
    		return new IncoherentEvent(controlFlowEntry, startTime, duration, incoherence);
    	}
        String label = interval.getLabel();
        if (label != null) {
        	return new NamedTimeEvent(controlFlowEntry, startTime, duration, (int) status, label);
        }
        return new TimeEvent(controlFlowEntry, startTime, duration, (int) status);
    }
	
	@Override
    protected List<@NonNull ILinkEvent> getLinkList(long zoomStartTime, long zoomEndTime, long resolution,
            @NonNull IProgressMonitor monitor) {
        List<@NonNull TimeGraphEntry> traceEntries = getEntryList(getTrace());
        if (traceEntries == null) {
            return Collections.emptyList();
        }
        List<@NonNull ILinkEvent> linkList = new ArrayList<>();
        List<@NonNull Long> times = StateSystemUtils.getTimes(zoomStartTime, zoomEndTime, resolution);
        TimeQueryFilter queryFilter = new TimeQueryFilter(times);

        for (TraceEntry entry : Iterables.filter(traceEntries, TraceEntry.class)) {
            ITimeGraphDataProvider<? extends TimeGraphEntryModel> provider = entry.getProvider();
            TmfModelResponse<List<ITimeGraphArrow>> response = provider.fetchArrows(queryFilter, monitor);
            List<ITimeGraphArrow> model = response.getModel();

            if (model != null) {
                Map<Long, ControlFlowEntry> map = fControlFlowEntries.row(entry);
                for (ITimeGraphArrow arrow : model) {
                    ITimeGraphEntry prevEntry = map.get(arrow.getSourceId());
                    ITimeGraphEntry nextEntry = map.get(arrow.getDestinationId());
                    if (prevEntry != null && nextEntry != null) {
                    	
                    	/* Check coherence of the arrow
                    	   If we lost some sched_switch events, the current thread may have not been updated correctly,
                    	   thus leading to the prevEntry not being the entry corresponding to the prev_tid, in which
                    	   case we want to fix that */
                    	int prevTid = ((ControlFlowEntry) prevEntry).getThreadId();
						ITmfEvent event = fIncoherentEvents.get(arrow.getStartTime());
						ITmfTrace trace = getTrace();
						if (event != null && trace instanceof IKernelTrace) {
							long value = (long) event.getContent().getField(((IKernelTrace) trace).getKernelEventLayout().fieldPrevTid()).getValue();
	                    	if (prevTid != value) {
	                    		System.out.println("fixing!");
								ControlFlowEntry newPrevEntry = map.get(getEntryQuarkFromTid((int) value));
	                    		linkList.add(new TimeLinkEvent(newPrevEntry, nextEntry, arrow.getStartTime(), arrow.getDuration(), 0));
	                    		continue;
	                    	}							
						}
						
						linkList.add(new TimeLinkEvent(prevEntry, nextEntry, arrow.getStartTime(), arrow.getDuration(), 0));
                    }
                }
            }
        }
        return linkList;
    }
	
	/**
	 * @see FlameGraphView.createTimeEventContextMenu
	 */
	private void createIncoherentEventContextMenu() {
        fEventMenuManager.setRemoveAllWhenShown(true);
        TimeGraphControl timeGraphControl = getTimeGraphViewer().getTimeGraphControl();
        final Menu timeEventMenu = fEventMenuManager.createContextMenu(timeGraphControl);

        timeGraphControl.addTimeEventMenuListener(new MenuDetectListener() {
            @Override
            public void menuDetected(MenuDetectEvent event) {
                Menu menu = timeEventMenu;
                // Create a context menu for IncoherentEvent only
                if (event.data instanceof IncoherentEvent) {
                    timeGraphControl.setMenu(menu);
                    return;
                }
                timeGraphControl.setMenu(null);
                event.doit = false;
            }
        });

        fEventMenuManager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                fillIncoherentEventContextMenu(fEventMenuManager);
                fEventMenuManager.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
            }
        });
        getSite().registerContextMenu(fEventMenuManager, getTimeGraphViewer().getSelectionProvider());
    }
	
	private void fillIncoherentEventContextMenu(@NonNull IMenuManager menuManager) {
        ISelection selection = getSite().getSelectionProvider().getSelection();
        if (selection instanceof IStructuredSelection) {
            for (Object object : ((IStructuredSelection) selection).toList()) {
                if (object instanceof IncoherentEvent) {
                	IncoherentEvent event = (IncoherentEvent) object;
                	ControlFlowEntry entry = (ControlFlowEntry) event.getEntry();
                    menuManager.add(new DisplayInferenceAction(event, entry, getEntryQuarkFromTid(entry.getThreadId()), 
                    		getTrace(entry)));
                }
            }
        }
    }
	
	/**
	 * Action that triggers the opening of the dialog used to 
	 * allow user-selection of a value for an inferred event field.
	 * 
	 * @author mmartin
	 *
	 */
	private final class InferenceSelectionAction extends Action {
		private final TimeGraphViewer fViewer;
		
		public InferenceSelectionAction(TimeGraphViewer timeGraphViewer) {
			fViewer= timeGraphViewer;
		}

		@Override
        public void run() {
			if (fModule != null) {
				if (fModule.hasMultiInferredEvents()) {
		        	Display display = Display.getDefault();
		    	    if (display != null) {
		        	    display.syncExec(new Runnable() { // syncExec to wait for the result of the dialog
		        	        @Override
		                    public void run() {
		        	        	Shell shell = fViewer.getControl().getShell();
		        	        	if (dialogs.get(getTrace()) == null) {
		        	        		dialogs.put(getTrace(), new InferenceDialog(shell, fModule)); // TODO should we recreate the dialog everytime?
		        	        	}
		        	        	dialogs.get(getTrace()).open();
		        	        }
		        	    });
			        }        	
		        }
			}
		}
	}
	
	private final class DisplayGlobalInferenceViewAction extends Action {

	    @Override
	    public void run() {
	    	try {
	    		/* Compute inferred events */
	    		List<TmfInferredEvent> inferredEvents = fModule.getInferredEvents();
	    		/* Create the InferenceTrace that will be used in the view */
		    	InferenceTrace newTrace = new InferenceTrace((TmfTrace) getTrace(), inferredEvents);
		    	/* Open the view */
		    	final IWorkbench wb = PlatformUI.getWorkbench();
		        final IWorkbenchPage activePage = wb.getActiveWorkbenchWindow().getActivePage();
	        	IViewPart view = activePage.showView(GlobalInferenceView.ID);
	        	if (view instanceof GlobalInferenceView) {
	        		GlobalInferenceView inferenceView = (GlobalInferenceView) view;
					inferenceView.setProperties(newTrace);
	        	}
			} catch (PartInitException e) {
				Activator.logError("Unable to open the view.", e);
			} catch (TmfTraceException e) {
				Activator.logError("Unable to open the view.", e);
			}
	        
	        super.run();
	    }
	    
	}
	
	protected IAction getInferenceSelectionAction() {
        if (fInferenceSelectionAction == null) {
            fInferenceSelectionAction = new InferenceSelectionAction(this.getTimeGraphViewer());
            fInferenceSelectionAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ICON_PATH));
            fInferenceSelectionAction.setText(LABEL_TEXT);
            fInferenceSelectionAction.setToolTipText(TOOLTIP_TEXT);
        }
        return fInferenceSelectionAction;
    }
	
	public IAction getGlobalInferenceViewAction() {
        if (fGlobalInferenceViewAction == null) {
        	fGlobalInferenceViewAction = new DisplayGlobalInferenceViewAction();
        	fGlobalInferenceViewAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ICON_PATH2));
        	fGlobalInferenceViewAction.setText(LABEL_TEXT2);
        	fGlobalInferenceViewAction.setToolTipText(TOOLTIP_TEXT2);
        }
        return fGlobalInferenceViewAction;
    }
	
	@Override
    protected void fillLocalToolBar(IToolBarManager manager) {
        // add "Select inferences" button to local tool bar of Coherence view
        IAction inferenceSelectionAction = getInferenceSelectionAction();
        manager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, inferenceSelectionAction);
        
        // add "Open global inference view" button
        IAction inferenceViewAction = getGlobalInferenceViewAction();
        manager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, inferenceViewAction);

        // add a separator to local tool bar
        manager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, new Separator());

        super.fillLocalToolBar(manager);
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
		
		// Create context menu for IncoherentEvent
		createIncoherentEventContextMenu();
	}

	public ControlFlowEntry findEntry(long quark) {
		TimeGraphEntry traceEntry = getEntryList(getTrace()).get(0);
		return fControlFlowEntries.get(traceEntry, quark);
	}
}
