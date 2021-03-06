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

package org.eclipse.tracecompass.incubator.coherence.core.readonly;

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlModelFactory;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlStateAttribute;
import org.eclipse.tracecompass.incubator.coherence.core.model.ITmfXmlStateValue;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlAction;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlCondition;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlFsm;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlLocation;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlMapEntry;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternEventHandler;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlPatternSegmentBuilder;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlState;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateChange;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlStateTransition;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlTimestampCondition;
import org.eclipse.tracecompass.incubator.coherence.core.model.TmfXmlTransitionValidator;
import org.eclipse.tracecompass.incubator.coherence.core.module.IXmlStateSystemContainer;
import org.eclipse.tracecompass.incubator.coherence.core.newmodel.TmfXmlScenarioModel;
import org.w3c.dom.Element;

/**
 * Concrete factory for XML model elements in read only mode
 *
 * @author Geneviève Bastien
 */
public class TmfXmlReadOnlyModelFactory implements ITmfXmlModelFactory {

    private static @Nullable ITmfXmlModelFactory fInstance = null;

    /**
     * Get the instance of this model creator
     *
     * @return The {@link TmfXmlReadOnlyModelFactory} instance
     */
    public static synchronized ITmfXmlModelFactory getInstance() {
        ITmfXmlModelFactory instance = fInstance;
        if (instance == null) {
            instance = new TmfXmlReadOnlyModelFactory();
            fInstance = instance;
        }
        return instance;
    }

    @Override
    public ITmfXmlStateAttribute createStateAttribute(Element attribute, IXmlStateSystemContainer container) {
        return new TmfXmlReadOnlyStateAttribute(this, attribute, container);
    }

    @Override
    public ITmfXmlStateValue createStateValue(Element node, IXmlStateSystemContainer container, List<ITmfXmlStateAttribute> attributes) {
        return new TmfXmlReadOnlyStateValue(this, node, container, attributes);
    }

    @Override
    public ITmfXmlStateValue createStateValue(Element node, IXmlStateSystemContainer container, String eventField) {
        return new TmfXmlReadOnlyStateValue(this, node, container, eventField);
    }

    @Override
    public TmfXmlCondition createCondition(Element node, IXmlStateSystemContainer container) {
        return TmfXmlCondition.create(this, node, container);
    }

    @Override
    public TmfXmlEventHandler createEventHandler(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlEventHandler(this, node, container);
    }

    @Override
    public TmfXmlStateChange createStateChange(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlStateChange(this, node, container);
    }

    @Override
    public TmfXmlLocation createLocation(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlLocation(this, node, container);
    }

    @Override
    public TmfXmlPatternEventHandler createPatternEventHandler(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlPatternEventHandler(this, node, container);
    }
    
    @Override
    public TmfXmlTransitionValidator createTransitionValidator(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlTransitionValidator(this, node, container);
    }

    @Override
    public TmfXmlAction createAction(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlAction(this, node, container);
    }

    @Override
    public TmfXmlFsm createFsm(Element node, IXmlStateSystemContainer container, TmfXmlScenarioModel scenarioModel) {
        return TmfXmlFsm.create(this, node, container, scenarioModel);
    }

    @Override
    public @NonNull TmfXmlState createState(Element node, IXmlStateSystemContainer container, @Nullable TmfXmlState parent) {
        return TmfXmlState.create(this, node, container, parent);
    }

    @Override
    public TmfXmlStateTransition createStateTransition(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlStateTransition(this, node, container);
    }

    @Override
    public TmfXmlTimestampCondition createTimestampsCondition(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlTimestampCondition(this, node, container);
    }

    @Override
    public TmfXmlPatternSegmentBuilder createPatternSegmentBuilder(Element node, IXmlStateSystemContainer container) {
        return new TmfXmlPatternSegmentBuilder(this, node, container);
    }

    @Override
    public @NonNull TmfXmlMapEntry createMapEntry(@NonNull Element node, @NonNull IXmlStateSystemContainer container) {
        return new TmfXmlMapEntry(this, node, container);
    }
}
