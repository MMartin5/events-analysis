package org.eclipse.tracecompass.incubator.coherence.core.newmodel;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class TmfXmlCpuScenarioModel extends TmfXmlScenarioModel {

	public TmfXmlCpuScenarioModel() {
		super();
	}

	@Override
	public List<String> getAttributesForEvent(ITmfEvent event) {
		List<String> attributes = new ArrayList<>();
		
		Object cpuAspect = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
		if (cpuAspect != null) {
			attributes.add(((Integer) cpuAspect).toString());
		}
		
		return attributes;
	}

}
