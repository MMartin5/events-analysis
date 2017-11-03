package org.eclipse.tracecompass.incubator.coherence.core.tests.benchmark;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.eclipse.tracecompass.incubator.coherence.core.module.XmlUtils;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.tests.Activator;
import org.eclipse.tracecompass.incubator.trace.lostevents.core.trace.LostEventsTrace;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.analysis.xml.core.tests.stateprovider.XmlModuleTestBase;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.tests.shared.TmfTestHelper;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CoherenceAnalysisBenchmark {

    /**
     * Test test ID for kernel analysis benchmarks
     */
    public static final String TEST_ID = "org.eclipse.tracecompass#Coherence checking#";
    private static final String TEST_BUILD = "Building Graph (%s)";
    private static final String TEST_MEMORY = "Memory Usage (%s)";

    private static final int LOOP_COUNT = 25;

    private interface RunMethod {
        void execute(PerformanceMeter pm, IAnalysisModule module);
    }

    private RunMethod cpu = (pm, module) -> {
        pm.start();
        TmfTestHelper.executeAnalysis(module);
        
//        module.schedule();
//        module.waitForCompletion();
        
        pm.stop();
    };

    private RunMethod memory = (pm, module) -> {
        System.gc();
        pm.start();
        TmfTestHelper.executeAnalysis(module);
        System.gc();
        pm.stop();
    };

    private static final Set<String> fTraceSet = new HashSet<>(Arrays.asList(
    		"/home/mmartin/Master/Traces/trace-sched-switch-delete100-109-with-lost/Sansfil-Securise-Etudiants-Lassonde-241-79.polymtl.ca/kernel/",
    		"/home/mmartin/Master/Traces/trace777/Sansfil-Securise-Etudiants-Lassonde-241-79.polymtl.ca/kernel/")); // FIXME
    
    private static final String fXMLAnalysisFile = "testfiles/kernel_analysis_from_fsm.xml";

    private static List<@NonNull IAnalysisModuleHelper> fModules = null;
    
    /**
     * Run all benchmarks
     */
    @Test
    public void runAllBenchmarks() {
        for (String trace : fTraceSet) {

            runOneBenchmark(trace,
                    String.format(TEST_BUILD, trace.toString()),
                    cpu,
                    Dimension.CPU_TIME);

            runOneBenchmark(trace,
                    String.format(TEST_MEMORY, trace.toString()),
                    memory,
                    Dimension.USED_JAVA_HEAP);
        }
    }

    /**
     * @see org.eclipse.tracecompass.tmf.analysis.xml.core.tests.model.FsmTest
     * 
     * @param testTrace
     * @param testName
     * @param method
     * @param dimension
     */
    private static void runOneBenchmark(@NonNull String testTrace, String testName, RunMethod method, Dimension dimension) {
        Performance perf = Performance.getDefault();
        PerformanceMeter pm = perf.createPerformanceMeter(TEST_ID + testName);
        perf.tagAsSummary(pm, "Execution graph " + testName, dimension);

        for (int i = 0; i < LOOP_COUNT; i++) {
        	LostEventsTrace trace = null;
        	XmlPatternAnalysis module = null;
        	
            try {
            	trace = new LostEventsTrace();
            	trace.initTrace(null, testTrace, TmfEvent.class, "benchmark_trace", LostEventsTrace.ID);
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
            	module = new XmlPatternAnalysis();
                module.setXmlFile(path.toFile().toPath());
                module.setName(XmlModuleTestBase.getName(node));
                
                String moduleId = node.getAttribute(TmfXmlStrings.ID);
                assertNotNull(moduleId);
                module.setId(moduleId);
                
                module.setTrace(trace);
                
                method.execute(pm, module);
            	
                /*
                 * Delete the supplementary files, so that the next iteration
                 * rebuilds the state system.
                 */
                File suppDir = new File(TmfTraceManager.getSupplementaryFileDir(trace));
                for (File file : suppDir.listFiles()) {
                    file.delete();
                }

            } catch (TmfTraceException e) {
                fail(e.getMessage());
			} catch (TmfAnalysisException e) {
				fail(e.getMessage());
			} catch (ParserConfigurationException e) {
				fail(e.getMessage());
			} catch (SAXException e) {
				fail(e.getMessage());
			} catch (IOException e) {
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
        pm.commit();
    }
    
}
