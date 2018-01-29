package org.eclipse.tracecompass.incubator.coherence.core.tests.benchmark;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.test.performance.Dimension;
import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.incubator.coherence.core.module.XmlUtils;
import org.eclipse.tracecompass.incubator.coherence.core.pattern.stateprovider.XmlPatternAnalysis;
import org.eclipse.tracecompass.incubator.coherence.core.tests.Activator;
import org.eclipse.tracecompass.incubator.trace.lostevents.core.trace.LostEventsTrace;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.analysis.xml.core.tests.stateprovider.XmlModuleTestBase;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.tests.shared.TmfTestHelper;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class KernelXMLAnalysisBenchmark {
	
	public static final String TEST_ID = "org.eclipse.tracecompass#Kernel XML Benchmark#";
	private static final String TEST_BUILD = "Running Coherence Analysis (%s) Using %s";
	private static final int LOOP_COUNT = 10;
	
	private interface RunMethod {
        void execute(PerformanceMeter pm, IAnalysisModule module);
    }

    private RunMethod cpu = (pm, module) -> {
        pm.start();
        TmfTestHelper.executeAnalysis(module);
        pm.stop();
    };

    private static final Set<String> fTraceSet = new HashSet<>(Arrays.asList(
    		"/home/mmartin/Master/tracecompass-test-traces/ctf/src/main/resources/trace2/")); // FIXME
    
    private static final String fXMLAnalysisFile = "testfiles/kernel_analysis_from_fsm.xml";
    
    private enum TestModule {

        JAVA_ANALYSIS("Java analysis"),
        XML_ANALYSIS("XML analysis");

        private final String fName;

        private TestModule(String name) {
            fName = name;
        }

        public String getTestNameString() {
            return fName;
        }

        public static IAnalysisModule getNewModule(TestModule moduleType) {
            switch (moduleType) {
            case JAVA_ANALYSIS:
            	KernelAnalysisModule kernelModule = new KernelAnalysisModule();
            	kernelModule.setId("test");
                return kernelModule;
            case XML_ANALYSIS:
                XmlPatternAnalysis module = new XmlPatternAnalysis(false);
                IPath path = Activator.getAbsoluteFilePath(fXMLAnalysisFile);
            	
            	// Get XML document
            	Document doc = null;
				try {
					doc = XmlUtils.getDocumentFromFile(path.toFile());
				} catch (ParserConfigurationException | SAXException | IOException e) {
					fail(e.getMessage());
				}
                assertNotNull(doc);

                /* get State Providers modules */
                NodeList stateproviderNodes = doc.getElementsByTagName(TmfXmlStrings.PATTERN);

                Element node = (Element) stateproviderNodes.item(0);
                assertNotNull(node);
                module.setXmlFile(path.toFile().toPath());
                module.setName(XmlModuleTestBase.getName(node));
                
                String moduleId = node.getAttribute(TmfXmlStrings.ID);
                assertNotNull(moduleId);
                module.setId(moduleId);
                
                return module;
            default:
                throw new IllegalStateException();
            }
        }
    }
    
    /**
     * Run all benchmarks
     */
    @Test
    public void runAllBenchmarks() {
        for (String trace : fTraceSet) {
        	
        	runOneBenchmark(trace,
                    String.format(TEST_BUILD, trace.toString(), TestModule.JAVA_ANALYSIS.getTestNameString()),
                    cpu,
                    Dimension.CPU_TIME, 
                    TestModule.JAVA_ANALYSIS);

            runOneBenchmark(trace,
                    String.format(TEST_BUILD, trace.toString(), TestModule.XML_ANALYSIS.getTestNameString()),
                    cpu,
                    Dimension.CPU_TIME, 
                    TestModule.XML_ANALYSIS);
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
    private static void runOneBenchmark(@NonNull String testTrace, String testName, RunMethod method, 
    		Dimension dimension, TestModule testModule) {
        Performance perf = Performance.getDefault();
        PerformanceMeter pm = perf.createPerformanceMeter(TEST_ID + testName);
        perf.tagAsSummary(pm, "Execution graph " + testName, dimension);

        for (int i = 0; i < LOOP_COUNT; i++) {
        	LostEventsTrace trace = null;
        	IAnalysisModule module = null;
        	
            try {
            	trace = new LostEventsTrace();
            	trace.initTrace(null, testTrace, TmfEvent.class, "benchmark_trace", LostEventsTrace.ID);
            	trace.traceOpened(new TmfTraceOpenedSignal(null, trace, null));
            	
            	// Create module
            	module = TestModule.getNewModule(testModule);
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
