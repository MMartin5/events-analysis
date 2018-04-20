package org.eclipse.tracecompass.incubator.internal.coherence.ui.views;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.model.ProcessStatus;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.coherence.ui.model.IncoherentEvent;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.Attributes;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.threadstatus.ThreadStatusDataProvider;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Activator;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.Messages;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.registry.LinuxStyle;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.controlflow.ControlFlowEntry;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.BaseDataProviderTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEventStyleStrings;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NamedTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class CoherencePresentationProvider extends TimeGraphPresentationProvider {
	
	private static final Map<Integer, StateItem> STATE_MAP;
    private static final List<StateItem> STATE_LIST;
    private static final StateItem[] STATE_TABLE;

    private static StateItem createState(LinuxStyle style) {
        int rgbInt = (int) style.toMap().getOrDefault(ITimeEventStyleStrings.fillColor(), 0);
        RGB color = new RGB(rgbInt >> 24 & 0xff, rgbInt >> 16 & 0xff, rgbInt >> 8 & 0xff);
        return new StateItem(color, style.getLabel()) {
            @Override
            public Map<String, Object> getStyleMap() {
                return style.toMap();
            }
        };
    }
    
    static {
        ImmutableMap.Builder<Integer, StateItem> builder = new ImmutableMap.Builder<>();
        /*
         * ADD STATE MAPPING HERE
         */
        builder.put(ProcessStatus.UNKNOWN.getStateValue().unboxInt(), createState(LinuxStyle.UNKNOWN));
        builder.put(ProcessStatus.RUN.getStateValue().unboxInt(), createState(LinuxStyle.USERMODE));
        builder.put(ProcessStatus.RUN_SYTEMCALL.getStateValue().unboxInt(), createState(LinuxStyle.SYSCALL));
        builder.put(ProcessStatus.INTERRUPTED.getStateValue().unboxInt(), createState(LinuxStyle.INTERRUPTED));
        builder.put(ProcessStatus.WAIT_BLOCKED.getStateValue().unboxInt(), createState(LinuxStyle.WAIT_BLOCKED));
        builder.put(ProcessStatus.WAIT_CPU.getStateValue().unboxInt(), createState(LinuxStyle.WAIT_FOR_CPU));
        builder.put(ProcessStatus.WAIT_UNKNOWN.getStateValue().unboxInt(), createState(LinuxStyle.WAIT_UNKNOWN));
        
        // Declare the new style, not included in LinuxStyle
        RGB incoherentStateColor = new RGB(100, 100, 100);
        int alpha = 255;
        float heightFactor = 0.50f;
        StateItem newStyle = new StateItem(incoherentStateColor, IncoherentEvent.INCOHERENT_MSG) {
            @Override
            public Map<String, Object> getStyleMap() {
                return ImmutableMap.of(ITimeEventStyleStrings.label(), IncoherentEvent.INCOHERENT_MSG,
                        ITimeEventStyleStrings.fillStyle(), ITimeEventStyleStrings.solidColorFillStyle(),
                        ITimeEventStyleStrings.fillColor(), incoherentStateColor.red << 24 | incoherentStateColor.green << 16 | incoherentStateColor.blue << 8 | alpha,
                        ITimeEventStyleStrings.heightFactor(), heightFactor);
            }
        };
        // Add the new style to the builder
        builder.put(IncoherentEvent.INCOHERENT_VALUE, newStyle);
        
        //builder.put()
        /*
         * DO NOT MODIFY AFTER
         */
        STATE_MAP = builder.build();
        STATE_LIST = ImmutableList.copyOf(STATE_MAP.values());
        STATE_TABLE = STATE_LIST.toArray(new StateItem[STATE_LIST.size()]);
    }

    /**
     * Average width of the characters used for state labels. Is computed in the
     * first call to postDrawEvent(). Is null before that.
     */
    private Integer fAverageCharacterWidth = null;

    /**
     * Default constructor
     */
    public CoherencePresentationProvider() {
        super(Messages.ControlFlowView_stateTypeName);
    }

    @Override
    public StateItem[] getStateTable() {
        return STATE_TABLE;
    }

    @Override
    public int getStateTableIndex(ITimeEvent event) {
        if (event instanceof TimeEvent && ((TimeEvent) event).hasValue()) {
            int status = ((TimeEvent) event).getValue();
            return STATE_LIST.indexOf(getMatchingState(status));
        }
        if (event instanceof NullTimeEvent) {
            return INVISIBLE;
        }
        return TRANSPARENT;
    }

    @Override
    public String getEventName(ITimeEvent event) {
        if (event instanceof TimeEvent) {
            TimeEvent ev = (TimeEvent) event;
            if (ev.hasValue()) {
                return getMatchingState(ev.getValue()).getStateString();
            }
        }
        return Messages.ControlFlowView_multipleStates;
    }

    private static StateItem getMatchingState(int status) {
        return STATE_MAP.getOrDefault(status, STATE_MAP.get(ProcessStatus.WAIT_UNKNOWN.getStateValue().unboxInt()));
    }
    
    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event, long hoverTime) {
        Map<String, String> retMap = super.getEventHoverToolTipInfo(event, hoverTime);
        if (retMap == null) {
            retMap = new LinkedHashMap<>(1);
        }

        if (!(event instanceof TimeEvent) || !((TimeEvent) event).hasValue() ||
                !(event.getEntry() instanceof ControlFlowEntry)) {
            return retMap;
        }

        ControlFlowEntry entry = (ControlFlowEntry) event.getEntry();
        ITimeGraphDataProvider<? extends TimeGraphEntryModel> dataProvider = BaseDataProviderTimeGraphView.getProvider(entry);
        TmfModelResponse<@NonNull Map<@NonNull String, @NonNull String>> response = dataProvider.fetchTooltip(
                new SelectionTimeQueryFilter(hoverTime, hoverTime, 1, Collections.singletonList(entry.getModel().getId())), null);
        Map<@NonNull String, @NonNull String> map = response.getModel();
        if (map != null) {
            String cpu = map.get(ThreadStatusDataProvider.CPU);
            if (cpu != null) {
                retMap.put(Messages.ControlFlowView_attributeCpuName, cpu);
            }
        }

        return retMap;
    }
    
    /**
     * Returns the average character width, measured in pixels, of the font
     * described by the receiver.
     *
     * @param gc
     *            The graphic context
     * @return the average character width of the font
     */
    @Deprecated
    private static int getAverageCharWidth(GC gc) {
        return gc.getFontMetrics().getAverageCharWidth();
    }
    
    @Override
    public void postDrawEvent(ITimeEvent event, Rectangle bounds, GC gc) {
        if (fAverageCharacterWidth == null) {
            fAverageCharacterWidth = getAverageCharWidth(gc);
        }
        if (bounds.width <= fAverageCharacterWidth || !(event instanceof NamedTimeEvent)) {
            // NamedTimeEvents are used only for the sys calls which we want.
            return;
        }
        NamedTimeEvent controlFlowEvent = (NamedTimeEvent) event;
        gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
        Utils.drawText(gc, controlFlowEvent.getLabel(), bounds.x, bounds.y, bounds.width, bounds.height, true, true);
    }

}
