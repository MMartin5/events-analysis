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
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.StatusLineContributionItem;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenario;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioHistoryBuilder;
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
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
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
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.google.common.collect.Multimap;


public class CoherenceView extends ControlFlowView {

	private final List<IMarkerEvent> fMarkers = Collections.synchronizedList(new ArrayList<>());
	private final List<ITmfEvent> fEvents = Collections.synchronizedList(new ArrayList<>()); // list of incoherent events

	public String COHERENCE_LABEL = "Incoherent";
	public String COHERENCE = "Coherence warning";
	private static final RGBA COHERENCE_COLOR = new RGBA(255, 0, 0, 50);
	
	public static String ID = "org.eclipse.tracecompass.incubator.coherence.ui.view"; 

	public static String FSM_ANALYSIS_ID = "kernel.linux.pattern.from.fsm";
		
	private CoherenceTooltipHandler fCoherenceToolTipHandler;
	private List<FsmStateIncoherence> fIncoherences = new ArrayList<>();
	
	private Map<String, Set<FsmStateIncoherence>> pEntries = Collections.synchronizedMap(new HashMap<>()); // pair of (entry id/scenario attribute, set of associated incoherent events)
	
	private final TimeGraphPresentationProvider fNewPresentation; // replace fPresentation from ControlFlowView
	
	private Job fJob;
	
	// TODO: should be removed when incubator analysis xml and tmf ones are merged
	Map<ITmfTrace, IAnalysisModule> fModules = new HashMap<>(); // pair of (trace, incubator analysis xml module)
	XmlPatternStateSystemModule fModule = null;
	
	private Map<ITmfStateInterval, FsmStateIncoherence> incoherencesMap = new HashMap<>(); // used to instantiate IncoherentEvent
	
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
	
	InferenceDialog dialog;

	public CoherenceView() {
	    super();
	    
	    fNewPresentation = new CoherencePresentationProvider();
	    dialog = null;
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
						fEvents.clear();
						fMarkers.clear();
						fIncoherences.clear();
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
					fEvents.clear();
					fMarkers.clear();
					fIncoherences.clear();
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
            fEvents.clear();
            fMarkers.clear();
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
	    
		XmlPatternAnalysis moduleParent = TmfTraceUtils.getAnalysisModuleOfClass(trace, XmlPatternAnalysis.class, FSM_ANALYSIS_ID);
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
									if (FSM_ANALYSIS_ID.equals(helper.getId())) {
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
        	
            List<ITmfEvent> events = fsm.getIncoherentEvents();
            fEvents.addAll(events);
            
            fIncoherences.addAll(fsm.getIncoherences());
            
            scenarios.putAll(fsm.getActiveScenariosList());
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
			int scenarioQuark = scenarios.get(String.valueOf(controlFlowEntry.getThreadId())).getScenarioInfos().getQuark();
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
					incoherencesMap.put(newInterval, incoherentEvent);
				
					if (!isCertainState(newInterval.getStartTime(), ss, certaintyStatusQuark)) {
						// TODO select another prev state because this one is not certain
						Activator.logWarning("Previous event coherence is uncertain");
					}
					
					newValue.add(newInterval);
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
								&& (incoherentEventTs < interval.getEndTime()))) {
					ITmfStateInterval newInterval1 = new TmfStateInterval(
							interval.getStartTime(), 
							incoherentEventTs - 1, 
							interval.getAttribute(), 
							IncoherentEvent.INCOHERENT_VALUE);
					incoherencesMap.put(newInterval1, incoherentEvent);
					ITmfStateInterval newInterval2 = new TmfStateInterval(
							incoherentEventTs, 
							interval.getEndTime(), 
							interval.getAttribute(), 
							interval.getValue());
					
					if (!isCertainState(newInterval1.getStartTime(), ss, certaintyStatusQuark)) {
						Activator.getDefault();
						// TODO select another prev state because this one is not certain
						Activator.logWarning("Previous event coherence is uncertain");
					}
					
					newValue.add(newInterval1);
					newValue.add(newInterval2);
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
        		FsmStateIncoherence incoherence = incoherencesMap.get(interval);
        		return new IncoherentEvent(controlFlowEntry, startTime, duration, incoherence);
        	}
            return new TimeEvent(controlFlowEntry, startTime, duration, (int) status);
        }
        return new NullTimeEvent(controlFlowEntry, startTime, duration);
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
                    menuManager.add(new DisplayInferenceAction(event, entry));
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
				XmlPatternStateProvider provider = fModule.getStateProvider();
				if (provider.hasMultiInferredEvents()) {
		        	Display display = Display.getDefault();
		    	    if (display != null) {
		        	    display.syncExec(new Runnable() { // syncExec to wait for the result of the dialog
		        	        @Override
		                    public void run() {
		        	        	Shell shell = fViewer.getControl().getShell();
		        	        	if (dialog == null) {
		        	        		dialog = new InferenceDialog(shell, provider); // TODO should we recreate the dialog everytime?
		        	        	}
		        	        	dialog.open();
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
	    		/* Create the InferenceTrace that will be used in the view */
		    	InferenceTrace newTrace = new InferenceTrace((TmfTrace) getTrace(), fModule.getStateProvider().getInferredEvents());
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
	
	private IAction getGlobalInferenceViewAction() {
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
}
