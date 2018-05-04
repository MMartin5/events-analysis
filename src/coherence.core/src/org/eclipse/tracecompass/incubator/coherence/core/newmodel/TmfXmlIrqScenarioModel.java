package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelTidAspect;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class TmfXmlIrqScenarioModel extends TmfXmlScenarioModel {

	public TmfXmlIrqScenarioModel() {
		super();
	}

	@Override
	public List<String> getAttributesForEvent(ITmfEvent event) {
		List<String> attributes = new ArrayList<>();
		
		Object tidAspect = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), KernelTidAspect.class, event);
		if (tidAspect != null) {
			attributes.add(((Integer) tidAspect).toString());
		}
		
		return attributes;
	}

}
