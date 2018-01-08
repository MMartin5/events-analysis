package org.eclipse.tracecompass.incubator.coherence.ui.widgets;

import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.internal.tmf.ui.Messages;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ILinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.ITimeDataProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.ITimeDataProviderConverter;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphTooltipHandler;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.Resolution;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.TimeFormat;

/**
 * @see TimeGraphTooltipHandler
 *
 */
public class CoherenceTooltipHandler {
	private static final String MIN_STRING = "< 0.01%"; //$NON-NLS-1$

    private static final double MIN_RATIO = 0.0001;

    private static final String MAX_STRING = "> 1000%"; //$NON-NLS-1$

    private static final int MAX_RATIO = 10;

    private static final int OFFSET = 40;

    private static final int HOVER_MAX_DIST = 10;

    private Shell fTipShell;
    private Composite fTipComposite;
    private ITimeDataProvider fTimeDataProvider;
    private ITimeGraphPresentationProvider fTimeGraphProvider = null;
    
    private IStatusLineManager fStatusLineManager = null; // FIXME we should not access the statusLineManager this way, but through Control

    /**
     * Standard constructor
     *
     * @param graphProv
     *            The presentation provider
     * @param timeProv
     *            The time provider
     */
    public CoherenceTooltipHandler(ITimeGraphPresentationProvider graphProv,
            ITimeDataProvider timeProv, IStatusLineManager statusManager) {

        this.fTimeGraphProvider = graphProv;
        this.fTimeDataProvider = timeProv;
        this.fStatusLineManager = statusManager;
    }

    /**
     * Set the time data provider
     *
     * @param timeDataProvider
     *            The time data provider
     */
    public void setTimeProvider(ITimeDataProvider timeDataProvider) {
        fTimeDataProvider = timeDataProvider;
    }

    private void createTooltipShell(Shell parent) {
        final Display display = parent.getDisplay();
        if (fTipShell != null && !fTipShell.isDisposed()) {
            fTipShell.dispose();
        }
        fTipShell = new Shell(parent, SWT.ON_TOP | SWT.TOOL);
        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;
        gridLayout.marginWidth = 2;
        gridLayout.marginHeight = 2;
        fTipShell.setLayout(gridLayout);
        fTipShell.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));

        fTipComposite = new Composite(fTipShell, SWT.NONE);
        fTipComposite.setLayout(new GridLayout(3, false));
        setupControl(fTipComposite);

    }

    /**
     * Callback for the mouse-over tooltip
     *
     * @param control
     *            The control object to use
     */
    public void activateHoverHelp(final Control control) {
        control.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                if (fTipShell != null && !fTipShell.isDisposed()) {
                    fTipShell.dispose();
                }
            }
        });

        control.addMouseMoveListener(new MouseMoveListener() {
            @Override
            public void mouseMove(MouseEvent e) {
                if (fTipShell != null && !fTipShell.isDisposed()) {
                    fTipShell.dispose();
                }
            }
        });

        control.addMouseTrackListener(new MouseTrackAdapter() {
            @Override
            public void mouseExit(MouseEvent e) {
                if (fTipShell != null && !fTipShell.isDisposed()) {
                    Point pt = control.toDisplay(e.x, e.y);
                    if (!fTipShell.getBounds().contains(pt)) {
                        fTipShell.dispose();
                    }
                }
            }
            
            private void fillValues(Point pt, TimeGraphControl timeGraphControl, ITimeGraphEntry entry) {
                if (entry == null) {
                    return;
                }
                if (entry.hasTimeEvents()) {
                    long currPixelTime = timeGraphControl.getTimeAtX(pt.x);
                    long nextPixelTime = timeGraphControl.getTimeAtX(pt.x + 1);
                    if (nextPixelTime == currPixelTime) {
                        nextPixelTime++;
                    }
                    ITimeEvent currEvent = Utils.findEvent(entry, currPixelTime, 0);
                    ITimeEvent nextEvent = Utils.findEvent(entry, currPixelTime, 1);

                    /*
                     * if there is no current event at the start of the current
                     * pixel range, or if the current event starts before the
                     * current pixel range, use the next event as long as it
                     * starts within the current pixel range
                     */
                    if ((currEvent == null || currEvent.getTime() < currPixelTime) &&
                            (nextEvent != null && nextEvent.getTime() < nextPixelTime)) {
                        currEvent = nextEvent;
                        currPixelTime = nextEvent.getTime();
                    }

                    /*
                     * if there is still no current event, use the closest
                     * between the next and previous event, as long as they are
                     * within a distance threshold
                     */
                    if (currEvent == null || currEvent instanceof NullTimeEvent) {
                        int nextDelta = Integer.MAX_VALUE;
                        int prevDelta = Integer.MAX_VALUE;
                        long nextTime = 0;
                        long prevTime = 0;
                        if (nextEvent != null && !(nextEvent instanceof NullTimeEvent)) {
                            nextTime = nextEvent.getTime();
                            nextDelta = Math.abs(timeGraphControl.getXForTime(nextTime) - pt.x);
                        }
                        ITimeEvent prevEvent = Utils.findEvent(entry, currPixelTime, -1);
                        if (prevEvent != null && !(prevEvent instanceof NullTimeEvent)) {
                            prevTime = prevEvent.getTime() + prevEvent.getDuration() - 1;
                            prevDelta = Math.abs(pt.x - timeGraphControl.getXForTime(prevTime));
                        }
                        if (nextDelta < HOVER_MAX_DIST && nextDelta <= prevDelta) {
                            currEvent = nextEvent;
                            currPixelTime = nextTime;
                        } else if (prevDelta < HOVER_MAX_DIST) {
                            currEvent = prevEvent;
                            currPixelTime = prevTime;
                        }
                    }

                    if (currEvent == null || currEvent instanceof NullTimeEvent) {
                        return;
                    }

                    // state
                    String state = fTimeGraphProvider.getEventName(currEvent);
                    if (state != null) {
                        if (currEvent instanceof IncoherentEvent) {
                        	// Add an item to the tooltip to display the incoherence
                        	String incoherence = ((IncoherentEvent) currEvent).getIncoherence();
                        	// Change the status to display the incoherence
                        	fStatusLineManager.setMessage("Incoherence: " + incoherence);
                        }
                    }
                }
            }

            @Override
            public void mouseHover(MouseEvent event) {
                if ((event.stateMask & SWT.BUTTON_MASK) != 0) {
                    return;
                }
                Point pt = new Point(event.x, event.y);
                TimeGraphControl timeGraphControl = (TimeGraphControl) event.widget;
                createTooltipShell(timeGraphControl.getShell());
                for (Control child : fTipComposite.getChildren()) {
                    child.dispose();
                }
                if ((event.stateMask & SWT.MODIFIER_MASK) != SWT.SHIFT) {
//                    ILinkEvent linkEvent = timeGraphControl.getArrow(pt); // cannot access protected getArrow because not in the same package
//                    if (linkEvent != null) {
//                        fillValues(linkEvent);
//                    }
                }
                if (fTipComposite.getChildren().length == 0) {
                    ITimeGraphEntry entry = timeGraphControl.getEntry(pt);
                    fillValues(pt, timeGraphControl, entry);
                }
                if (fTipComposite.getChildren().length == 0) {
                    return;
                }
                fTipShell.pack();
                Point tipPosition = control.toDisplay(pt);
                fTipShell.pack();
                setHoverLocation(fTipShell, tipPosition);
                fTipShell.setVisible(true);
            }
        });
    }

    private static void setHoverLocation(Shell shell, Point position) {
        Rectangle displayBounds = shell.getDisplay().getBounds();
        Rectangle shellBounds = shell.getBounds();
        if (position.x + shellBounds.width + OFFSET > displayBounds.width && position.x - shellBounds.width - OFFSET >= 0) {
            shellBounds.x = position.x - shellBounds.width - OFFSET;
        } else {
            shellBounds.x = Math.max(Math.min(position.x + OFFSET, displayBounds.width - shellBounds.width), 0);
        }
        if (position.y + shellBounds.height + OFFSET > displayBounds.height && position.y - shellBounds.height - OFFSET >= 0) {
            shellBounds.y = position.y - shellBounds.height - OFFSET;
        } else {
            shellBounds.y = Math.max(Math.min(position.y + OFFSET, displayBounds.height - shellBounds.height), 0);
        }
        shell.setBounds(shellBounds);
    }

    private void setupControl(Control control) {
        control.setForeground(fTipShell.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        control.setBackground(fTipShell.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));

        control.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                fTipShell.dispose();
            }
        });

        control.addMouseTrackListener(new MouseTrackAdapter() {
            @Override
            public void mouseExit(MouseEvent e) {
                fTipShell.dispose();
            }
        });

        control.addMouseMoveListener(new MouseMoveListener() {
            @Override
            public void mouseMove(MouseEvent e) {
                fTipShell.dispose();
            }
        });
    }
}
