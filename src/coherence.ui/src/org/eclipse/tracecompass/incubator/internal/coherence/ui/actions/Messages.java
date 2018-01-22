package org.eclipse.tracecompass.incubator.internal.coherence.ui.actions;

import org.eclipse.osgi.util.NLS;

/**
 * Action messages
 */
public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.tracecompass.incubator.internal.coherence.ui.actions.messages"; //$NON-NLS-1$
    /**
     * Display inference message
     */
    public static String DisplayInferenceAction_display;
    
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
