/*******************************************************************************
 * Copyright (c) 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.coherence.core.readwrite;

import java.util.ArrayList;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.coherence.core.Activator;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlModelFactory;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlStateAttribute;
import org.eclipse.tracecompass.incubator.coherence.core.model.Messages;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlScenarioInfo;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateValue;
import org.eclipse.tracecompass.incubator.coherence.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.incubator.coherence.core.module.XmlUtils;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlStrings;
import org.eclipse.tracecompass.tmf.analysis.xml.core.module.TmfXmlUtils;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.StateSystemBuilderUtils;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.w3c.dom.Element;

/**
 * Implements a state value in a read write mode. See {@link TmfXmlStateValue}
 * for the syntax of the state value.
 *
 * In read/write mode, a state value can be considered as an assignation where
 * the state value is assigned to the quark represented by the state attributes
 *
 * @author Geneviève Bastien
 */
public class TmfXmlReadWriteStateValue extends TmfXmlStateValue {

    private static final String ILLEGAL_STATE_EXCEPTION_MESSAGE = "The state system hasn't been initialized yet"; //$NON-NLS-1$

    /**
     * Constructor where the path to the value is a list of state attributes
     *
     * @param modelFactory
     *            The factory used to create XML model elements
     * @param node
     *            The state value XML element
     * @param container
     *            The state system container this state value belongs to
     * @param attributes
     *            The attributes representing the path to this value
     */
    public TmfXmlReadWriteStateValue(TmfXmlReadWriteModelFactory modelFactory, Element node, IXmlStateSystemContainer container, List<ITmfXmlStateAttribute> attributes) {
        this(modelFactory, node, container, attributes, null);
    }

    /**
     * Constructor where the path to the value is an event field
     *
     * @param modelFactory
     *            The factory used to create XML model elements
     * @param node
     *            The state value XML element
     * @param container
     *            The state system container this state value belongs to
     * @param eventField
     *            The event field where to get the value
     */
    public TmfXmlReadWriteStateValue(TmfXmlReadWriteModelFactory modelFactory, Element node, IXmlStateSystemContainer container, String eventField) {
        this(modelFactory, node, container, new ArrayList<ITmfXmlStateAttribute>(), eventField);
    }

    private TmfXmlReadWriteStateValue(ITmfXmlModelFactory modelFactory, Element node, IXmlStateSystemContainer container, List<ITmfXmlStateAttribute> attributes, @Nullable String eventField) {
        super(modelFactory, node, container, attributes, eventField);
    }

    @Override
    protected @Nullable ITmfStateSystemBuilder getStateSystem() {
        return (ITmfStateSystemBuilder) super.getStateSystem();
    }

    @Override
    protected TmfXmlStateValueBase initializeStateValue(ITmfXmlModelFactory modelFactory, Element node) {
        TmfXmlStateValueBase stateValueType = null;
        /* Process the XML Element state value */
        String type = node.getAttribute(TmfXmlStrings.TYPE);
        String value = getSsContainer().getAttributeValue(node.getAttribute(TmfXmlStrings.VALUE));
        String forcedTypeName = node.getAttribute(TmfXmlStrings.FORCED_TYPE);
        ITmfStateValue.Type forcedType = forcedTypeName.isEmpty() ? ITmfStateValue.Type.NULL : TmfXmlUtils.getTmfStateValueByName(forcedTypeName);

        if (value == null && getStackType().equals(ValueTypeStack.NULL)) {
            throw new IllegalStateException();
        }

        List<@Nullable Element> children = XmlUtils.getChildElements(node);
        List<ITmfXmlStateAttribute> childAttributes = new ArrayList<>();
        List<TmfXmlStateValue> childStateValues = new ArrayList<>();
        for (Element child : children) {
            if (child == null) {
                continue;
            }
            if (child.getNodeName().equals(TmfXmlStrings.STATE_VALUE)) {
                TmfXmlStateValue stateValue = (TmfXmlStateValue) modelFactory.createStateValue(child, getSsContainer(), new ArrayList<ITmfXmlStateAttribute>());
                childStateValues.add(stateValue);
            } else {
                ITmfXmlStateAttribute queryAttribute = modelFactory.createStateAttribute(child, getSsContainer());
                childAttributes.add(queryAttribute);
            }
        }

        switch (type) {
        case TmfXmlStrings.TYPE_INT: {
            /* Integer value */
            ITmfStateValue stateValue = value != null && !value.isEmpty() ?
                    TmfXmlUtils.newTmfStateValueFromObjectWithForcedType(Integer.parseInt(value), forcedType) : TmfStateValue.nullValue();
            stateValueType = new TmfXmlStateValueTmf(stateValue, childAttributes);
            break;
        }
        case TmfXmlStrings.TYPE_LONG: {
            /* Long value */
            ITmfStateValue stateValue = value != null && !value.isEmpty() ?
                    TmfXmlUtils.newTmfStateValueFromObjectWithForcedType(Long.parseLong(value), forcedType) : TmfStateValue.nullValue();
            stateValueType = new TmfXmlStateValueTmf(stateValue, childAttributes);
            break;
        }
        case TmfXmlStrings.TYPE_STRING: {
            /* String value */
            ITmfStateValue stateValue = value != null ?
                    TmfXmlUtils.newTmfStateValueFromObjectWithForcedType(value, forcedType) : TmfStateValue.nullValue();
            stateValueType = new TmfXmlStateValueTmf(stateValue, childAttributes);
            break;
        }
        case TmfXmlStrings.TYPE_NULL: {
            /* Null value */
            ITmfStateValue stateValue = TmfStateValue.nullValue();
            stateValueType = new TmfXmlStateValueTmf(stateValue, childAttributes);
            break;
        }
        case TmfXmlStrings.EVENT_FIELD:
            /* Event field */
            if (value == null) {
                throw new IllegalStateException("Event field name cannot be null"); //$NON-NLS-1$
            }
            stateValueType = new TmfXmlStateValueEventField(value);
            break;
        case TmfXmlStrings.TYPE_EVENT_NAME:
            /* The value is the event name */
            stateValueType = new TmfXmlStateValueEventName();
            break;
        case TmfXmlStrings.TYPE_DELETE:
            /* Deletes the value of an attribute */
            stateValueType = new TmfXmlStateValueDelete();
            break;
        case TmfXmlStrings.TYPE_QUERY:
            /* Value is the result of a query */
            stateValueType = new TmfXmlStateValueQuery(childAttributes);
            break;
        case TmfXmlStrings.TYPE_SCRIPT:
            /* Value is the returned value from a script execution */
            if (value == null) {
                throw new IllegalStateException(Messages.TmfXmlStateValue_ScriptNullException);
            }
            String scriptEngine = node.getAttribute(TmfXmlStrings.SCRIPT_ENGINE);
            stateValueType = new TmfXmlStateValueScript(scriptEngine, value, childStateValues, forcedType);
            break;
        default:
            throw new IllegalArgumentException(String.format("TmfXmlStateValue constructor: unexpected element %s for stateValue type", type)); //$NON-NLS-1$
        }
        return stateValueType;
    }

    // ----------------------------------------------------------
    // Internal state value classes for the different types
    // ----------------------------------------------------------

    /**
     * Base class for all state value. Contain default methods to handle event,
     * process or increment the value
     */
    protected abstract class TmfXmlStateValueTypeReadWrite extends TmfXmlStateValueBase {

        @Override
        public final void handleEvent(ITmfEvent event, int quark, long timestamp, @Nullable TmfXmlScenarioInfo scenarioInfo) throws StateValueTypeException, TimeRangeException, AttributeNotFoundException {
            if (isIncrement()) {
                incrementValue(event, quark, timestamp, scenarioInfo);
            } else {
                ITmfStateValue value = getValue(event, scenarioInfo);
                processValue(quark, timestamp, value);
            }
        }

        @Override
        protected void processValue(int quark, long timestamp, ITmfStateValue value) throws AttributeNotFoundException, TimeRangeException, StateValueTypeException {
            ITmfStateSystemBuilder ss = getStateSystem();
            if (ss == null) {
                throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_MESSAGE);
            }
            switch (getStackType()) {
            case POP:
                ss.popAttribute(timestamp, quark);
                break;
            case PUSH:
                ss.pushAttribute(timestamp, value, quark);
                break;
            case NULL:
            case PEEK:
            default:
                if (isUpdate()) {
                    ss.updateOngoingState(value, quark);
                } else {
                    ss.modifyAttribute(timestamp, value, quark);
                }
                break;
            }
        }

        @Override
        protected void incrementValue(ITmfEvent event, int quark, long timestamp, @Nullable TmfXmlScenarioInfo scenarioInfo) throws StateValueTypeException, TimeRangeException, AttributeNotFoundException {
            ITmfStateSystemBuilder ss = getStateSystem();
            if (ss == null) {
                throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_MESSAGE);
            }
            StateSystemBuilderUtils.incrementAttributeInt(ss, timestamp, quark, 1);
        }
    }

    private static @Nullable ITmfStateValue incrementByType(int quark, ITmfStateSystem ss, ITmfStateValue stateValue) {
        ITmfStateValue value = null;
        switch (stateValue.getType()) {
        case LONG: {
            long incrementLong = stateValue.unboxLong();
            ITmfStateValue currentState = ss.queryOngoingState(quark);
            long currentValue = (currentState.isNull() ? 0 : currentState.unboxLong());
            value = TmfStateValue.newValueLong(incrementLong + currentValue);
            return value;
        }
        case INTEGER: {
            int increment = stateValue.unboxInt();
            ITmfStateValue currentState = ss.queryOngoingState(quark);
            int currentValue = (currentState.isNull() ? 0 : currentState.unboxInt());
            value = TmfStateValue.newValueInt(increment + currentValue);
            return value;
        }
        case DOUBLE:
        case NULL:
        case STRING:
        case CUSTOM:
        default:
        }
        return value;
    }

    /* This state value uses a constant value, defined in the XML */
    private class TmfXmlStateValueTmf extends TmfXmlStateValueTypeReadWrite {

        private final ITmfStateValue fValue;
        private final List<ITmfXmlStateAttribute> fAttributesValue;

        public TmfXmlStateValueTmf(ITmfStateValue value, List<ITmfXmlStateAttribute> attributes) {
            fValue = value;
            fAttributesValue = attributes;
        }

        @Override
        public ITmfStateValue getValue(@Nullable ITmfEvent event, @Nullable TmfXmlScenarioInfo scenarioInfo) {
            try {
                switch (getStackType()) {
                case PEEK:
                    return peek(event, scenarioInfo);
                case PUSH:
                case NULL:
                case POP:
                default:
                    return fValue;
                }
            } catch (AttributeNotFoundException | StateSystemDisposedException e) {
                Activator.logError("Query stack failed"); //$NON-NLS-1$
                return TmfStateValue.nullValue();
            }
        }

        /**
         * @param event
         *            The ongoing event
         * @param scenarioInfo
         *            The active scenario details. The value should be null if
         *            there no scenario.
         * @return The value value at the top of the stack without removing it
         * @throws AttributeNotFoundException
         *             If the do not exist
         * @throws StateSystemDisposedException
         *             If the state system is disposed
         */
        private ITmfStateValue peek(@Nullable ITmfEvent event, @Nullable TmfXmlScenarioInfo scenarioInfo) throws AttributeNotFoundException, StateSystemDisposedException {
            int quarkQuery = IXmlStateSystemContainer.ROOT_QUARK;
            ITmfStateSystemBuilder ss = getStateSystem();
            if (ss == null) {
                throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_MESSAGE);
            }

            if (event == null) {
                throw new IllegalStateException("The event should not be null at this point."); //$NON-NLS-1$
            }

            for (ITmfXmlStateAttribute attribute : fAttributesValue) {
                quarkQuery = attribute.getAttributeQuark(event, quarkQuery, scenarioInfo);
                if (quarkQuery == IXmlStateSystemContainer.ERROR_QUARK) {
                    /*
                     * the query is not valid, we stop the state change
                     */
                    return TmfStateValue.nullValue();
                }
            }

            final long ts = event.getTimestamp().toNanos();
            @Nullable ITmfStateInterval stackTopInterval = StateSystemUtils.querySingleStackTop(ss, ts, quarkQuery);
            final ITmfStateValue value = stackTopInterval != null ? stackTopInterval.getStateValue() : null;
            return value != null ? value : TmfStateValue.nullValue();
        }

        @Override
        public void incrementValue(ITmfEvent event, int quark, long timestamp, @Nullable TmfXmlScenarioInfo scenarioInfo) throws StateValueTypeException, TimeRangeException, AttributeNotFoundException {
            ITmfStateSystem ss = getStateSystem();
            if (ss == null) {
                throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_MESSAGE);
            }
            ITmfStateValue value = incrementByType(quark, ss, fValue);
            if (value != null) {
                processValue(quark, timestamp, value);
            } else {
                Activator.logWarning("TmfXmlStateValue: The increment value is not a number type"); //$NON-NLS-1$
            }
        }

        @Override
        public String toString() {
            return "Value=" + fValue; //$NON-NLS-1$
        }

    }

    /* The state value uses the value of an event field */
    public class TmfXmlStateValueEventField extends TmfXmlStateValueTypeReadWrite {

        private final String fFieldName;

        public TmfXmlStateValueEventField(String field) {
            fFieldName = field;
        }

        @Override
        public ITmfStateValue getValue(@Nullable ITmfEvent event, @Nullable TmfXmlScenarioInfo scenarioInfo) {
            if (event == null) {
                Activator.logWarning("XML State value: requested an event field, but event is null"); //$NON-NLS-1$
                return TmfStateValue.nullValue();
            }
            return getEventFieldValue(event, fFieldName);
        }

        @Override
        public void incrementValue(ITmfEvent event, int quark, long timestamp, @Nullable TmfXmlScenarioInfo scenarioInfo) throws StateValueTypeException, TimeRangeException, AttributeNotFoundException {
            ITmfStateSystem ss = getSsContainer().getStateSystem();
            if (ss == null) {
                throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_MESSAGE);
            }
            ITmfStateValue incrementValue = getValue(event, scenarioInfo);
            ITmfStateValue value = incrementByType(quark, ss, incrementValue);
            if (value != null) {
                processValue(quark, timestamp, value);
            } else {
                Activator.logWarning(String.format("TmfXmlStateValue: The event field increment %s is not a number type but a %s", fFieldName, incrementValue.getType())); //$NON-NLS-1$
            }
        }

        @Override
        public String toString() {
            return "Event Field=" + fFieldName; //$NON-NLS-1$
        }
        
        public String getFieldName() {
        	return fFieldName;
        }
    }

    /* The state value is the event name */
    private class TmfXmlStateValueEventName extends TmfXmlStateValueTypeReadWrite {

        @Override
        public @NonNull ITmfStateValue getValue(@Nullable ITmfEvent event, @Nullable TmfXmlScenarioInfo scenarioInfo) throws AttributeNotFoundException {
            if (event == null) {
                Activator.logWarning("XML State value: request event name, but event is null"); //$NON-NLS-1$
                return TmfStateValue.nullValue();
            }
            return TmfStateValue.newValueString(event.getName());
        }

        @Override
        public String toString() {
            return "Event name"; //$NON-NLS-1$
        }
    }

    /* The state value deletes an attribute */
    private class TmfXmlStateValueDelete extends TmfXmlStateValueTypeReadWrite {

        @Override
        public @NonNull ITmfStateValue getValue(@Nullable ITmfEvent event, @Nullable TmfXmlScenarioInfo scenarioInfo) throws AttributeNotFoundException {
            return TmfStateValue.nullValue();
        }

        @Override
        protected void processValue(int quark, long timestamp, ITmfStateValue value) throws TimeRangeException, AttributeNotFoundException {
            ITmfStateSystem ss = getStateSystem();
            if (!(ss instanceof ITmfStateSystemBuilder)) {
                throw new IllegalStateException("incrementValue should never be called when not building the state system"); //$NON-NLS-1$
            }
            ITmfStateSystemBuilder builder = (ITmfStateSystemBuilder) ss;
            builder.removeAttribute(timestamp, quark);
        }

        @Override
        public String toString() {
            return "Delete"; //$NON-NLS-1$
        }
    }

    /* The state value uses the result of a query */
    private class TmfXmlStateValueQuery extends TmfXmlStateValueTypeReadWrite {

        private final List<ITmfXmlStateAttribute> fQueryValue;

        public TmfXmlStateValueQuery(List<ITmfXmlStateAttribute> childAttributes) {
            fQueryValue = childAttributes;
        }

        @Override
        public ITmfStateValue getValue(@Nullable ITmfEvent event, @Nullable TmfXmlScenarioInfo scenarioInfo) throws AttributeNotFoundException {
            /* Query the state system for the value */
            ITmfStateValue value = TmfStateValue.nullValue();
            int quarkQuery = IXmlStateSystemContainer.ROOT_QUARK;
            ITmfStateSystem ss = getStateSystem();
            if (ss == null) {
                throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_MESSAGE);
            }

            for (ITmfXmlStateAttribute attribute : fQueryValue) {
                quarkQuery = attribute.getAttributeQuark(event, quarkQuery, scenarioInfo);
                if (quarkQuery == IXmlStateSystemContainer.ERROR_QUARK) {
                    /* the query is not valid, we stop the state change */
                    break;
                }
            }
            /*
             * the query can fail : for example, if a value is requested but has
             * not been set yet
             */
            if (quarkQuery != IXmlStateSystemContainer.ERROR_QUARK) {
                value = ss.queryOngoingState(quarkQuery);
            }
            return value;
        }

        @Override
        public void incrementValue(ITmfEvent event, int quark, long timestamp, @Nullable TmfXmlScenarioInfo scenarioInfo) throws StateValueTypeException, TimeRangeException, AttributeNotFoundException {
            ITmfStateSystem ss = getStateSystem();
            if (ss == null) {
                throw new IllegalStateException(ILLEGAL_STATE_EXCEPTION_MESSAGE);
            }

            ITmfStateValue incrementValue = getValue(event, scenarioInfo);
            ITmfStateValue value = incrementByType(quark, ss, incrementValue);
            if (value != null) {
                processValue(quark, timestamp, value);
            } else {
                Activator.logWarning("TmfXmlStateValue: The query result increment is not a number type"); //$NON-NLS-1$
            }
        }

        @Override
        public String toString() {
            return "Query=" + fQueryValue; //$NON-NLS-1$
        }
    }

    /* The state value uses the returned value from a script execution */
    private class TmfXmlStateValueScript extends TmfXmlStateValueTypeReadWrite {

        public static final String DEFAULT_SCRIPT_ENGINE = "nashorn"; //$NON-NLS-1$
        private final List<TmfXmlStateValue> fChildStateValues;
        private final String fScriptEngine;
        private final String fScript;
        private final ITmfStateValue.Type fForcedType;

        public TmfXmlStateValueScript(String scriptEngine, String script, List<TmfXmlStateValue> childStateValues, ITmfStateValue.Type forcedType) {
            fScriptEngine = !scriptEngine.isEmpty() ? scriptEngine : DEFAULT_SCRIPT_ENGINE;
            fScript = script;
            fChildStateValues = childStateValues;
            fForcedType = forcedType;
        }

        @Override
        public ITmfStateValue getValue(@Nullable ITmfEvent event, @Nullable TmfXmlScenarioInfo scenarioInfo) throws AttributeNotFoundException {
            Object result = null;
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName(fScriptEngine);

            for (TmfXmlStateValue stateValue : fChildStateValues) {
                String stateValueID = stateValue.getID();
                if (stateValueID != null) {
                    ITmfStateValue value = stateValue.getValue(event, scenarioInfo);
                    switch (value.getType()) {
                    case LONG:
                        engine.put(stateValueID, value.unboxLong());
                        break;
                    case INTEGER:
                        engine.put(stateValueID, value.unboxInt());
                        break;
                    case STRING:
                        engine.put(stateValueID, value.unboxStr());
                        break;
                    case DOUBLE:
                    case CUSTOM:
                    case NULL:
                    default:
                    }
                } else {
                    Activator.logWarning(Messages.TmfXmlStateValue_MissingScriptChildrenID);
                }
            }

            try {
                result = engine.eval(fScript);
            } catch (ScriptException e) {
                Activator.logError("Script execution failed", e); //$NON-NLS-1$
                return TmfStateValue.nullValue();
            }

            return TmfXmlUtils.newTmfStateValueFromObjectWithForcedType(result, fForcedType);
        }

        @Override
        public String toString() {
            return "Script=" + fScript; //$NON-NLS-1$
        }
    }

}
