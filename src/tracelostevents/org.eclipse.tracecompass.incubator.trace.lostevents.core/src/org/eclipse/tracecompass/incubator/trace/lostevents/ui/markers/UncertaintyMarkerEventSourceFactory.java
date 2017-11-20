package org.eclipse.tracecompass.incubator.trace.lostevents.ui.markers;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.trace.AbstractTmfTraceAdapterFactory;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.IMarkerEventSource;

public class UncertaintyMarkerEventSourceFactory extends AbstractTmfTraceAdapterFactory {
	
	IMarkerEventSource adapter;

	@Override
	public Class<?>[] getAdapterList() {
		return new Class[] {
                IMarkerEventSource.class
        };
	}

	@Override
	protected <T> @Nullable T getTraceAdapter(@NonNull ITmfTrace trace, Class<T> adapterType) {
		if (IMarkerEventSource.class.equals(adapterType)) {
            adapter = new UncertaintyMarkerEventSource(trace);
            return adapterType.cast(adapter);
        }
        return null;
	}
}
