package org.eclipse.tracecompass.incubator.coherence.core.tests.benchmark;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.tests.stubs.trace.TmfXmlKernelTraceStub;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfInferredEvent;
import org.eclipse.tracecompass.incubator.coherence.core.module.XmlUtils;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlScenarioObserver;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.tests.Activator;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.analysis.xml.core.tests.stateprovider.XmlModuleTestBase;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.tests.shared.TmfTestHelper;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CoherenceAnalysisEvaluation {
    
	/* Relative path to the folder containing the evaluation traces */
    private static final String fTraceFolder = "test_traces/eval_traces_3/"; // modify this path to test a different set of traces
    
    private static final String fXMLAnalysisFile = "testfiles/eval_fsm.xml";
    
    /**
     * Run all benchmarks
     */
    @Test
    public void runAllBenchmarks() {
    	File folder = new File(fTraceFolder);
        for (File file : folder.listFiles()) {
        	String trace = file.getAbsolutePath();
            runOneBenchmark(trace, TmfXmlScenarioObserver.ALGO1);
        }
    }

    /**
     * @see org.eclipse.tracecompass.tmf.analysis.xml.core.tests.model.FsmTest
     * @see org.eclipse.tracecompass.lttng2.kernel.core.tests.perf.analysis.execgraph.KernelExecutionGraphBenchmark
     * 
     * @param testTrace
     * @param testName
     * @param method
     * @param dimension
     */
    private static void runOneBenchmark(@NonNull String testTrace, String algo) {
                
        TmfXmlKernelTraceStub trace = null;
    	XmlPatternAnalysis module = null;
    	
        try {
        	trace = new TmfXmlKernelTraceStub();
        	trace.initTrace(null, testTrace, TmfEvent.class, "evaluation_trace", "org.eclipse.tracecompass.analysis.os.linux.core.kernel.trace.stub");        		
        	trace.traceOpened(new TmfTraceOpenedSignal(null, trace, null));
        	
        	IPath path = Activator.getAbsoluteFilePath(fXMLAnalysisFile);
        	
        	// Get XML document
        	Document doc = XmlUtils.getDocumentFromFile(path.toFile());
            assertNotNull(doc);

            /* get State Providers modules */
            NodeList stateproviderNodes = doc.getElementsByTagName(TmfXmlStrings.PATTERN);

            Element node = (Element) stateproviderNodes.item(0);
            assertNotNull(node);
                           
        	// Create module
        	module = new XmlPatternAnalysis(true);
            module.setXmlFile(path.toFile().toPath());
            module.setName(XmlModuleTestBase.getName(node));
            
            String moduleId = node.getAttribute(TmfXmlStrings.ID);
            assertNotNull(moduleId);
            module.setId(moduleId);
            
            module.setTrace(trace);            
            module.getStateSystemModule().changeCoherenceAlgorithm(algo); // set the algorithm we want to test        	
            TmfTestHelper.executeAnalysis(module); // run the analysis
            List<TmfInferredEvent> inferredEvents = module.getStateSystemModule().getInferredEvents(); // get inferred events
            
            System.out.println("TRACE " + testTrace);
            for (TmfInferredEvent inferred : inferredEvents) {
            	System.out.println(inferred.toString());
            }
            
//            int error = 0;
//            for (TmfInferredEvent inferred : inferredEvents) {
//            	if (!inferred.getName().equals(deletedEvent.get(inferredEvents.indexOf(inferred)).getName())) {
//            		error++;
//            	}
//            }
//            float accuracy = (deletedEvent.size() - error) / deletedEvent.size() * 100;
//            System.out.println(String.format("accuracy = %d  %", accuracy));
        	
            /*
             * Delete the supplementary files, so that the next iteration
             * rebuilds the state system.
             */
            File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
            for (File file : suppDir.listFiles()) {
                file.delete();
            }
            if (module != null) {
            	module.dispose();
            }
            
		} catch (TmfAnalysisException e) {
			fail(e.getMessage());
		} catch (ParserConfigurationException e) {
			fail(e.getMessage());
		} catch (SAXException e) {
			fail(e.getMessage());
		} catch (IOException e) {
			fail(e.getMessage());
		} catch (TmfTraceException e) {
			fail(e.getMessage());
		} finally {
            if (module != null) {
            	module.dispose();
            }
            if (trace != null) {
                trace.dispose();
            }
        }
    }    
}
