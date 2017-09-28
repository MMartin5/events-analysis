package org.eclipse.tracecompass.incubator.coherence.ui.views;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelTidAspect;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateProvider;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternStateSystemModule;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.Activator;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.module.Messages;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.module.XmlUtils;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.ITmfXmlSchemaParser;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlUtils;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ILinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.MarkerEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeLinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphColorScheme;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.eclipse.tracecompass.incubator.coherence.module.TmfAnalysisModuleHelperXml;
import org.eclipse.tracecompass.incubator.coherence.module.TmfAnalysisModuleHelperXml.XmlAnalysisModuleType;

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

	public CoherenceView() {
	    super();
	}

	@Override
	public void dispose() {
	    super.dispose();
	}

	@Override
    @TmfSignalHandler
	public void traceSelected(@Nullable TmfTraceSelectedSignal signal) {
		// Make sure we don't request data twice for the same trace
		if (getTrace() != signal.getTrace()) {
		    super.traceSelected(signal);
		    fEvents.clear();
		    fMarkers.clear();
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
        super.traceOpened(signal);
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
    
    /*
     * Legacy (Linux Tools) XML directory.
     * TODO Remove once we feel the transition phase is over.
     */
    private final IPath XML_DIRECTORY_LEGACY =
            Activator.getDefault().getStateLocation().removeLastSegments(1)
            .append("org.eclipse.linuxtools.tmf.analysis.xml.core") //$NON-NLS-1$
            .append("xml_files"); //$NON-NLS-1$
    
    private TmfAnalysisModuleHelperXml populateAnalysisModule() {
        IPath pathToFiles = XmlUtils.getXmlFilesPath();
        File folder = pathToFiles.toFile();
        if (!(folder.isDirectory() && folder.exists())) {
            return null;
        }
        /*
         * Transfer files from Linux Tools directory.
         */
        File oldFolder = XML_DIRECTORY_LEGACY.toFile();
        final File[] oldAnalysisFiles = oldFolder.listFiles();
        if (oldAnalysisFiles != null) {
            for (File fromFile : oldAnalysisFiles) {
                File toFile = pathToFiles.append(fromFile.getName()).toFile();
                if (!toFile.exists() && !fromFile.isDirectory()) {
                    try (FileInputStream fis = new FileInputStream(fromFile);
                            FileOutputStream fos = new FileOutputStream(toFile);
                            FileChannel source = fis.getChannel();
                            FileChannel destination = fos.getChannel();) {
                        destination.transferFrom(source, 0, source.size());
                    } catch (IOException e) {
                        String error = Messages.XmlUtils_ErrorCopyingFile;
                        Activator.logError(error, e);
                    }
                }
            }
        }
        TmfAnalysisModuleHelperXml helper = null;
        Map<String, @NonNull File> files = XmlUtils.listFiles();
        for (File xmlFile : files.values()) {
            helper = processFile(xmlFile);
            if (helper != null) {
            	break;
            }
        }
        return helper;
    }
    
    private List<@NonNull IAnalysisModuleHelper> fModules = null;
    
    private TmfAnalysisModuleHelperXml processFile(@NonNull File xmlFile) {
        if (!XmlUtils.xmlValidate(xmlFile).isOK()) {
            return null;
        }

        TmfAnalysisModuleHelperXml helper = null;
        
        try {
            Document doc = XmlUtils.getDocumentFromFile(xmlFile);

            // Get the pattern module
            NodeList patternNodes = doc.getElementsByTagName(TmfXmlStrings.PATTERN);
            for (int i = 0; i < patternNodes.getLength(); i++) {
                Element node = (Element) patternNodes.item(i);
                
                if (node.getAttribute(TmfXmlStrings.ID).equals(FSM_ANALYSIS_ID) ) {
                	helper = new TmfAnalysisModuleHelperXml(xmlFile, node, XmlAnalysisModuleType.PATTERN);
                	break;
                }
            }
            	
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Activator.logError("Error opening XML file", e); //$NON-NLS-1$
        }
        
        return helper;
    }

	/**
	 * Run the XML analysis and collect the state machines from the analysis
	 */
	public void requestData() {
	    ITmfTrace trace = checkNotNull(getTrace());
	    
	    @Nullable
		IAnalysisModule moduleParent = TmfTraceUtils.getAnalysisModuleOfClass(trace, XmlPatternAnalysis.class, FSM_ANALYSIS_ID);
        if (moduleParent == null) {
    	    TmfAnalysisModuleHelperXml helper = populateAnalysisModule();
    	    if (helper == null) {
    	    	return;
    	    }
			try {
				moduleParent = helper.newModule(trace);
			} catch (TmfAnalysisException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
	    
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
                ITimeGraphEntry threadEntry = null;
                for (TimeGraphEntry entry : traceEntry.getChildren()) {
                    if (entry instanceof ControlFlowEntry) {
                        if (((ControlFlowEntry) entry).getThreadId() == KernelTidAspect.INSTANCE.resolve(event)) {
                            threadEntry = entry;
                        }
                    }
                }
                if (threadEntry == null) {
                    continue;
                }

                long ts = event.getTimestamp().getValue();
                ControlFlowEntry cfEntry = (ControlFlowEntry) threadEntry;
                int tid = cfEntry.getThreadId();
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
            }
        }
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
                    IMarkerEvent marker = new MarkerEvent(null, eventTime, 1, COHERENCE, COHERENCE_COLOR, COHERENCE_LABEL, true);
                    if (!fMarkers.contains(marker)) {
                        fMarkers.add(marker);
                    }
                }
            }
	    }

	    return fMarkers;
	}

	/**
	 * Construct coherence links from the map of (incoherent, previous coherent) events
	 *
	 * @return
	 *         The list of links used by the coherence view
	 */
	public List<@NonNull ILinkEvent> getCoherenceLinks() {
	    List<ILinkEvent> links = new ArrayList<>();
	    if (fEvents.isEmpty()) {
            return Collections.emptyList();
        }

	    for (ITmfEvent incoherentEvent : fEvents.keySet()) {
	        ITmfEvent prevEvent = fEvents.get(incoherentEvent);
	        if (prevEvent == null) {
	            return Collections.emptyList();
	        }

	        // Use incoherentEvent because prevEvent may not have any payload if we used the traceBeginning event
	        Integer tid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(getTrace(), LinuxTidAspect.class, incoherentEvent);
	        if (tid == null) {
	            return Collections.emptyList();
	        }

	        // We need only one entry because incoherentEvent and prevEvent have same entries (same thread)
	        ITimeGraphEntry entry = null;
	        entry = findEntry(getTrace(), tid, incoherentEvent.getTimestamp().getValue());

	        // TODO draw arrows backwards
	        int value = TimeGraphColorScheme.RED_STATE;
	        links.add(new TimeLinkEvent(entry, entry, prevEvent.getTimestamp().getValue(),
	                incoherentEvent.getTimestamp().getValue() - prevEvent.getTimestamp().getValue(), value));

	        // Look for the original TimeEvent in the entry and replace it by an IncoherentEvent
	        Iterator<@NonNull ? extends ITimeEvent> it = entry.getTimeEventsIterator();
	        List<ITimeEvent> newList = new ArrayList<>();
	        while (it.hasNext()) {
	            ITimeEvent event = it.next();
	            if (event.getTime() == prevEvent.getTimestamp().getValue()) {
	                long incoherentDuration = incoherentEvent.getTimestamp().getValue() - prevEvent.getTimestamp().getValue();
	                IncoherentEvent newIncoherent = new IncoherentEvent(entry, prevEvent.getTimestamp().getValue(), incoherentDuration);
	                newList.add(newIncoherent);
	                // Add the end of the original state as a new TimeEvent if necessary
	                if (event.getDuration() != incoherentDuration) {
	                    TimeEvent subEvent = new TimeEvent(entry, event.getTime() + incoherentDuration,
	                            event.getDuration() - incoherentDuration, ((TimeEvent) event).getValue());
	                    newList.add(subEvent);
	                }

	            }
	            else {
	                newList.add(event);
	            }
	        }
	        // Reset the event lists of the entry with the new events
	        ((TimeGraphEntry) entry).setEventList(newList);
	        ((TimeGraphEntry) entry).setZoomedEventList(newList);
        }
	    return links;
	}

	@Override
	protected List<@NonNull ILinkEvent> getLinkList(long zoomStartTime, long zoomEndTime, long resolution,
            @NonNull IProgressMonitor monitor) {
	    List<@NonNull ILinkEvent> linkList = new ArrayList<>();
	    // Look for "normal" links for ControlFlow view
	    linkList = super.getLinkList(zoomStartTime, zoomEndTime, resolution, monitor);
	    if (linkList == null) {
	        return Collections.emptyList();
	    }
	    // Add the coherence links
	    linkList.addAll(getCoherenceLinks());
	    return linkList;
	}
}
