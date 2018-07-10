package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class TmfXmlEvalScenarioModel extends TmfXmlScenarioModel {
	
	private final IKernelAnalysisEventLayout fLayout;

	public TmfXmlEvalScenarioModel(IKernelAnalysisEventLayout layout) {
		super();
		
		fLayout = layout;
	}

	@Override
	public List<String> getAttributesForEvent(ITmfEvent event) {
		List<String> attributes = new ArrayList<>();
		ITmfEventField content = event.getContent();
    	
    	ITmfEventField attr = content.getField("obj_id");
    	if (attr != null) {
    		attributes.add(attr.getValue().toString());
    	}
	    	    	
    	return attributes;
	}

}
