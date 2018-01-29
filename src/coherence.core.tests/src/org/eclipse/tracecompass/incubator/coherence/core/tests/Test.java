package org.eclipse.tracecompass.incubator.coherence.core.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.tests.stubs.trace.TmfXmlKernelTraceStub;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.incubator.coherence.core.module.XmlUtils;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.trace.layout.LttngEventLayout;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.analysis.xml.core.tests.stateprovider.XmlModuleTestBase;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEventField;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.junit.After;
import org.junit.Before;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Test {


    private static final @NonNull String TEST_TRACE = "test_traces/testTrace.xml";
    private static final @NonNull String TEST_ANALYSIS = "testfiles/simple_fsm.xml";

    ITmfTrace fTrace;
    XmlPatternAnalysis fModule;
    
    private final List<TmfInferredEventTest> expectedValues = initExpectedValues();
    
    /**
     * Initializes the expected values for inferred events
     * 
     * @return
     * 			The list of expected events
     */
    private static List<TmfInferredEventTest> initExpectedValues() {
    	List<TmfInferredEventTest> expectedValues = new ArrayList<>();
    	
    	ITmfTimestamp start = TmfTimestamp.create(13, ITmfTimestamp.NANOSECOND_SCALE);
        ITmfTimestamp end = TmfTimestamp.create(19, ITmfTimestamp.NANOSECOND_SCALE);
        List<TmfEventField> fields = Arrays.asList(new TmfEventField("cpu", 2, null));
        ITmfEventField content = new TmfEventField(ITmfEventField.ROOT_FIELD_ID, null, 
        		fields.toArray(new TmfEventField[fields.size()]));
        
        TmfInferredEventTest expected = new TmfInferredEventTest("exit", start, end, 1, content);
        expectedValues.add(expected);
        
        return expectedValues;
    }

    /**
     * Initializes the trace and the module for the tests
     *
     * @throws TmfAnalysisException
     *             Any exception thrown during module initialization
     */
    @Before
    public void setUp() throws TmfAnalysisException {
    	// Initialize trace
    	TmfXmlKernelTraceStub trace = new TmfXmlKernelTraceStub();
    	trace.setKernelEventLayout(LttngEventLayout.getInstance());
        IPath filePath = Activator.getAbsoluteFilePath(TEST_TRACE);
        IStatus status = trace.validate(null, filePath.toOSString());
        if (!status.isOK()) {
            fail(status.getException().getMessage());
        }
        try {
            trace.initTrace(null, filePath.toOSString(), TmfEvent.class);
        } catch (TmfTraceException e) {
            fail(e.getMessage());
        }
        // Delete supplementary files
        File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
        for (File file : suppDir.listFiles()) {
            file.delete();
        }
        ((TmfTrace) trace).traceOpened(new TmfTraceOpenedSignal(this, trace, null));
    	// Initialize analysis module
        try {        
	        IPath path = Activator.getAbsoluteFilePath(TEST_ANALYSIS);
	        Document doc = XmlUtils.getDocumentFromFile(path.toFile());
	        assertNotNull(doc);
	        
	        /* get State Providers modules */
	        NodeList stateproviderNodes = doc.getElementsByTagName(TmfXmlStrings.PATTERN);
	        
	        Element node = (Element) stateproviderNodes.item(0);
	        assertNotNull(node);
	        
	        // Create module
	        XmlPatternAnalysis module = new XmlPatternAnalysis(true);
	        module.setXmlFile(path.toFile().toPath());
	        module.setName(XmlModuleTestBase.getName(node));
	        
	        String moduleId = node.getAttribute(TmfXmlStrings.ID);
	        assertNotNull(moduleId);
	        module.setId(moduleId);
	        
	        module.setTrace(trace);
	        // Execute analysis
	        module.schedule();
	        module.waitForCompletion();
	        
	        fTrace = trace;
	        fModule = module;
	        
		} catch (ParserConfigurationException e) {
			fail(e.getMessage());
		} catch (SAXException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		}
                       
    }

    /**
     * Dispose the module and the trace
     */
    @After
    public void cleanUp() {
        fTrace.dispose();
        fModule.dispose();
    }

    /**
     * Display the inferences
     */
    @org.junit.Test
    public void testFsm() {
        XmlPatternAnalysis module = fModule;
        assertNotNull(module);
        
        List<TmfInferredEvent> inferredEvents = module.getStateSystemModule().getStateProvider().getInferredEvents();
        
        if (inferredEvents.size() != expectedValues.size()) {
        	fail("Some missing events have not been inferred.");
        }
        
        Iterator<TmfInferredEvent> inferredIt = inferredEvents.iterator();
        Iterator<TmfInferredEventTest> expectedIt = expectedValues.iterator();
        while (inferredIt.hasNext()) {
        	TmfInferredEvent event = inferredIt.next();
        	System.out.println(event.toString());
        	
        	TmfInferredEventTest expectedEvent = expectedIt.next();
        	
        	if (!expectedEvent.equals(event)) {
            	fail("Expected event does not match inferred event.");
            }
        	
        	if (!expectedEvent.getContent().equals(event.getContent())) {
        		fail("Expected event content does not match inferred event content.");
        	}
        }
    }

}
