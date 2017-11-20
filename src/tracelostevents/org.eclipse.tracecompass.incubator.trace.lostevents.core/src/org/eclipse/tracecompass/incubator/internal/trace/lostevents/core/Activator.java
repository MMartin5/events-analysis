/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.trace.lostevents.core;

import org.eclipse.tracecompass.common.core.TraceCompassActivator;
import org.eclipse.tracecompass.incubator.trace.lostevents.core.trace.LostEventsTrace;
import org.eclipse.tracecompass.incubator.trace.lostevents.ui.markers.UncertaintyMarkerEventSourceFactory;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceAdapterManager;

/**
 * Activator
 */
public class Activator extends TraceCompassActivator {

    /** The plug-in ID */
    public static final String PLUGIN_ID = "org.eclipse.tracecompass.incubator.trace.lostevents.core"; //$NON-NLS-1$

    private UncertaintyMarkerEventSourceFactory fUncertaintyMarkerEventSourceFactory;

    /**
     * The constructor
     */
    public Activator() {
        super(PLUGIN_ID);
    }

    /**
     * Returns the instance of this plug-in
     *
     * @return The plugin instance
     */
    public static TraceCompassActivator getInstance() {
        return TraceCompassActivator.getInstance(PLUGIN_ID);
    }

    @Override
    protected void startActions() {
        fUncertaintyMarkerEventSourceFactory = new UncertaintyMarkerEventSourceFactory();
        TmfTraceAdapterManager.registerFactory(fUncertaintyMarkerEventSourceFactory, LostEventsTrace.ID);
    }

    @Override
    protected void stopActions() {
        TmfTraceAdapterManager.unregisterFactory(fUncertaintyMarkerEventSourceFactory);
        fUncertaintyMarkerEventSourceFactory.dispose();
    }

}

