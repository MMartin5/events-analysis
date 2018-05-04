package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class TmfXmlProcessScenarioModel extends TmfXmlScenarioModel {
	
	private final IKernelAnalysisEventLayout fLayout;

	public TmfXmlProcessScenarioModel(IKernelAnalysisEventLayout layout) {
		super();
		
		fLayout = layout;
	}

	@Override
	public List<String> getAttributesForEvent(ITmfEvent event) {
		List<String> attributes = new ArrayList<>();
		ITmfEventField content = event.getContent();
    	
    	// We want to collect tid information for process FSM
    	if (event.getName().equals("sched_switch")) {
	    	ITmfEventField prevTid = content.getField(fLayout.fieldPrevTid());
	    	if (prevTid != null) {
	    		attributes.add(prevTid.getValue().toString());
	    	}
	    	
	    	ITmfEventField nextTid = content.getField(fLayout.fieldNextTid());
	    	if (nextTid != null) {
	    		attributes.add(nextTid.getValue().toString());
	    	}
    	}
    	else {
	    	ITmfEventField childTid = content.getField(fLayout.fieldChildTid());
	    	if (childTid != null) {
	    		attributes.add(childTid.getValue().toString());
	    	}
	    	
			Object tidAspect = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), KernelTidAspect.class, event);
			if (tidAspect != null) {
				attributes.add(((Integer) tidAspect).toString());
			}
    	}
    	
    	return attributes;
	}

}
