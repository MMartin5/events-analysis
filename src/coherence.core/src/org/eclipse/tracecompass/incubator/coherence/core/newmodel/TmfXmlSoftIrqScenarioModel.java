package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;

public class TmfXmlSoftIrqScenarioModel extends TmfXmlScenarioModel {
	
	private final IKernelAnalysisEventLayout fLayout;

	public TmfXmlSoftIrqScenarioModel(IKernelAnalysisEventLayout layout) {
		super();
		
		fLayout = layout;
	}

	@Override
	public List<String> getAttributesForEvent(ITmfEvent event) {
		List<String> attributes = new ArrayList<>();
		ITmfEventField content = event.getContent();

		ITmfEventField vecField = content.getField(fLayout.fieldVec());
    	if (vecField != null) {
    		attributes.add(vecField.getValue().toString());
    	}
	
    	return attributes;
	}

}
