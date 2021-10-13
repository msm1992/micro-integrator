/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.SequenceMediatorSerializer;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.json.JSONObject;
import org.wso2.carbon.inbound.endpoint.internal.http.api.APIResource;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SequenceResource extends APIResource {

    private static Log LOG = LogFactory.getLog(SequenceResource.class);

    private static final String SEQUENCE_NAME = "sequenceName";

    public SequenceResource(String urlTemplate){
        super(urlTemplate);
    }

    @Override
    public Set<String> getMethods() {
        Set<String> methods = new HashSet<>();
        methods.add(Constants.HTTP_GET);
        methods.add(Constants.HTTP_POST);
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext) {

        buildMessage(messageContext);
        org.apache.axis2.context.MessageContext axisMsgCtx =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        String seqName = Utils.getQueryParameter(messageContext, SEQUENCE_NAME);
        if (messageContext.isDoingGET()) {
            if (Objects.nonNull(seqName)) {
                populateSequenceData(messageContext, seqName);
            } else {
                populateSequenceList(messageContext);
            }
        } else {
            handlePost(messageContext, axisMsgCtx);
        }
        return true;
    }

    private void handlePost(MessageContext msgCtx, org.apache.axis2.context.MessageContext axisMsgCtx) {

        JSONObject response;
        try {
            JsonObject payload = Utils.getJsonPayload(axisMsgCtx);
            if (payload.has(Constants.NAME)) {
                String seqName = payload.get(Constants.NAME).getAsString();
                SynapseConfiguration configuration = msgCtx.getConfiguration();
                SequenceMediator sequence = configuration.getDefinedSequences().get(seqName);
                if (sequence != null) {
                    String performedBy = msgCtx.getProperty(Constants.USERNAME_PROPERTY).toString();
                    JSONObject info = new JSONObject();
                    info.put(SEQUENCE_NAME, seqName);
                    response = Utils.handleTracing(performedBy, Constants.AUDIT_LOG_TYPE_SEQUENCE_TRACE, info,
                                                   sequence.getAspectConfiguration(), seqName, axisMsgCtx);
                } else {
                    response = Utils.createJsonError("Specified sequence ('" + seqName + "') not found", axisMsgCtx,
                            Constants.BAD_REQUEST);
                }
            } else {
                response = Utils.createJsonError("Unsupported operation", axisMsgCtx, Constants.BAD_REQUEST);
            }
            Utils.setJsonPayLoad(axisMsgCtx, response);
        } catch (IOException e) {
            LOG.error("Error when parsing JSON payload", e);
            Utils.setJsonPayLoad(axisMsgCtx, Utils.createJsonErrorObject("Error when parsing JSON payload"));
        }
    }

    private void populateSequenceList(MessageContext messageContext) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        SynapseConfiguration configuration = messageContext.getConfiguration();

        Map<String, SequenceMediator> sequenceMediatorMap = configuration.getDefinedSequences();

        JSONObject jsonBody = Utils.createJSONList(sequenceMediatorMap.size());

        for (SequenceMediator sequence: sequenceMediatorMap.values()) {

            JSONObject sequenceObject = new JSONObject();

            sequenceObject.put(Constants.NAME, sequence.getName());
            sequenceObject.put(Constants.CONTAINER, sequence.getArtifactContainerName());

            String statisticState = sequence.getAspectConfiguration().isStatisticsEnable() ? Constants.ENABLED : Constants.DISABLED;
            sequenceObject.put(Constants.STATS, statisticState);

            String tracingState = sequence.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED;
            sequenceObject.put(Constants.TRACING, tracingState);

            jsonBody.getJSONArray(Constants.LIST).put(sequenceObject);
        }
        Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
    }

    private void populateSequenceData(MessageContext messageContext, String sequenceName) {

        org.apache.axis2.context.MessageContext axis2MessageContext =
                ((Axis2MessageContext) messageContext).getAxis2MessageContext();

        JSONObject jsonBody = getSequenceByName(messageContext, sequenceName);

        if (Objects.nonNull(jsonBody)) {
            Utils.setJsonPayLoad(axis2MessageContext, jsonBody);
        } else {
            axis2MessageContext.setProperty(Constants.HTTP_STATUS_CODE, Constants.NOT_FOUND);
        }
    }

    private JSONObject getSequenceByName(MessageContext messageContext, String sequenceName) {

        SynapseConfiguration configuration = messageContext.getConfiguration();
        SequenceMediator sequence = configuration.getDefinedSequences().get(sequenceName);
        return convertSequenceToJsonObject(sequence);
    }

    private JSONObject convertSequenceToJsonObject(SequenceMediator sequenceMediator) {

        if (Objects.isNull(sequenceMediator)) {
            return null;
        }

        JSONObject sequenceObject = new JSONObject();

        sequenceObject.put(Constants.NAME, sequenceMediator.getName());
        sequenceObject.put(Constants.CONTAINER, sequenceMediator.getArtifactContainerName());

        String statisticState = sequenceMediator.getAspectConfiguration().isStatisticsEnable() ? Constants.ENABLED : Constants.DISABLED;
        sequenceObject.put(Constants.STATS, statisticState);

        String tracingState = sequenceMediator.getAspectConfiguration().isTracingEnabled() ? Constants.ENABLED : Constants.DISABLED;
        sequenceObject.put(Constants.TRACING, tracingState);

        List<Mediator> mediators = sequenceMediator.getList();
        String []mediatorTypes = new String[mediators.size()];
        for(int i = 0; i < mediators.size(); i++){
            mediatorTypes[i] = mediators.get(i).getType();
        }
        sequenceObject.put("mediators", mediatorTypes);
        sequenceObject.put(Constants.SYNAPSE_CONFIGURATION,
                new SequenceMediatorSerializer().serializeSpecificMediator(sequenceMediator));

        return sequenceObject;
    }
}
