/*
﻿Developed with the contribution of the European Commission - Directorate General for Maritime Affairs and Fisheries
© European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can
redistribute it and/or modify it under the terms of the GNU General Public License as published by the
Free Software Foundation, either version 3 of the License, or any later version. The IFDM Suite is distributed in
the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details. You should have received a
copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.europa.ec.fisheries.uvms.rules.service.boundary;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.MovementRefType;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.MovementRefTypeType;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.MovementType;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.RecipientInfoType;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.SetReportMovementType;
import eu.europa.ec.fisheries.schema.exchange.plugin.types.v1.EmailType;
import eu.europa.ec.fisheries.schema.exchange.plugin.types.v1.PluginType;
import eu.europa.ec.fisheries.schema.exchange.service.v1.ServiceResponseType;
import eu.europa.ec.fisheries.schema.rules.movement.v1.RawMovementType;
import eu.europa.ec.fisheries.uvms.commons.message.api.MessageException;
import eu.europa.ec.fisheries.uvms.exchange.model.exception.ExchangeModelMapperException;
import eu.europa.ec.fisheries.uvms.exchange.model.mapper.ExchangeDataSourceResponseMapper;
import eu.europa.ec.fisheries.uvms.exchange.model.mapper.ExchangeModuleRequestMapper;
import eu.europa.ec.fisheries.uvms.rules.message.constants.DataSourceQueue;
import eu.europa.ec.fisheries.uvms.rules.message.consumer.RulesResponseConsumer;
import eu.europa.ec.fisheries.uvms.rules.message.producer.RulesMessageProducer;
import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesModelMarshallException;
import eu.europa.ec.fisheries.uvms.rules.service.business.MovementFact;
import eu.europa.ec.fisheries.uvms.rules.service.mapper.ExchangeMovementMapper;

@Stateless
public class ExchangeServiceBean {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeServiceBean.class);
    
    @Inject
    private RulesResponseConsumer consumer;
    
    @Inject
    private RulesMessageProducer producer;
    
    public void sendBackToExchange(String guid, RawMovementType rawMovement, MovementRefTypeType status, String username) throws RulesModelMarshallException, MessageException {
        LOG.info("[INFO] Sending back processed movement to exchange");

        // Map response
        MovementRefType movementRef = new MovementRefType();
        movementRef.setMovementRefGuid(guid);
        movementRef.setType(status);
        movementRef.setAckResponseMessageID(rawMovement.getAckResponseMessageID());

        // Map movement
        SetReportMovementType setReportMovementType = ExchangeMovementMapper.mapExchangeMovement(rawMovement);

        try {
            String exchangeResponseText = ExchangeMovementMapper.mapToProcessedMovementResponse(setReportMovementType, movementRef, username);
            producer.sendDataSourceMessage(exchangeResponseText, DataSourceQueue.EXCHANGE);
        } catch (ExchangeModelMapperException e) {
            e.printStackTrace();
        }
    }
    
    public List<ServiceResponseType> getPluginList(PluginType pluginType) throws ExchangeModelMapperException, MessageException {
        ArrayList<PluginType> types = new ArrayList<>();
        types.add(pluginType);
        String serviceListRequest = ExchangeModuleRequestMapper.createGetServiceListRequest(types);
        String serviceListRequestId = producer.sendDataSourceMessage(serviceListRequest, DataSourceQueue.EXCHANGE);
        TextMessage serviceListResponse = consumer.getMessage(serviceListRequestId, TextMessage.class);
        return ExchangeDataSourceResponseMapper.mapToServiceTypeListFromModuleResponse(serviceListResponse, serviceListRequestId);
    }
    
    public void sendReportToPlugin(ServiceResponseType service, PluginType pluginType, String ruleName, String endpoint, MovementType exchangeMovement, List<RecipientInfoType> recipientInfoList, MovementFact fact) throws ExchangeModelMapperException, MessageException {
        String exchangeRequest = ExchangeModuleRequestMapper.createSendReportToPlugin(service.getServiceClassName(), pluginType, new Date(), ruleName, endpoint, exchangeMovement, recipientInfoList, fact.getAssetName(), fact.getIrcs(), fact.getMmsiNo(), fact.getExternalMarking(), fact.getFlagState());
        String messageId = producer.sendDataSourceMessage(exchangeRequest, DataSourceQueue.EXCHANGE);
        consumer.getMessage(messageId, TextMessage.class);
    }
    
    public void sendEmail(ServiceResponseType service, EmailType email, String ruleName) throws ExchangeModelMapperException, MessageException {
        String request = ExchangeModuleRequestMapper.createSetCommandSendEmailRequest(service.getServiceClassName(), email, ruleName);
        producer.sendDataSourceMessage(request, DataSourceQueue.EXCHANGE);
    }
}