package eu.europa.ec.fisheries.uvms.rules.service.bean;


import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.ec.fisheries.schema.rules.customrule.v1.CustomRuleType;
import eu.europa.ec.fisheries.schema.rules.module.v1.CountTicketsByMovementsRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.GetCustomRuleRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.GetTicketsAndRulesByMovementsRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.GetTicketsAndRulesByMovementsResponse;
import eu.europa.ec.fisheries.schema.rules.module.v1.GetTicketsByMovementsRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.PingResponse;
import eu.europa.ec.fisheries.schema.rules.module.v1.RulesBaseRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.RulesModuleMethod;
import eu.europa.ec.fisheries.schema.rules.module.v1.SetFLUXFAReportMessageRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.SetFLUXMDRSyncMessageRulesRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.SetMovementReportRequest;
import eu.europa.ec.fisheries.schema.rules.movement.v1.RawMovementType;
import eu.europa.ec.fisheries.schema.rules.source.v1.GetTicketListByMovementsResponse;
import eu.europa.ec.fisheries.uvms.audit.model.exception.AuditModelMarshallException;
import eu.europa.ec.fisheries.uvms.audit.model.mapper.AuditLogMapper;
import eu.europa.ec.fisheries.uvms.rules.message.constants.DataSourceQueue;
import eu.europa.ec.fisheries.uvms.rules.message.event.CountTicketsByMovementsEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.ErrorEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.GetCustomRuleReceivedEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.GetTicketsAndRulesByMovementsEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.GetTicketsByMovementsEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.PingReceivedEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.SetFLUXFAReportMessageReceivedEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.SetFLUXMDRSyncMessageReceivedEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.SetMovementReportReceivedEvent;
import eu.europa.ec.fisheries.uvms.rules.message.event.carrier.EventMessage;
import eu.europa.ec.fisheries.uvms.rules.message.exception.MessageException;
import eu.europa.ec.fisheries.uvms.rules.message.producer.RulesMessageProducer;
import eu.europa.ec.fisheries.uvms.rules.model.constant.AuditObjectTypeEnum;
import eu.europa.ec.fisheries.uvms.rules.model.constant.AuditOperationEnum;
import eu.europa.ec.fisheries.uvms.rules.model.constant.FaultCode;
import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesFaultException;
import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesModelMapperException;
import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesModelMarshallException;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.JAXBMarshaller;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.ModuleResponseMapper;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.RulesModuleResponseMapper;
import eu.europa.ec.fisheries.uvms.rules.service.EventService;
import eu.europa.ec.fisheries.uvms.rules.service.RulesService;
import eu.europa.ec.fisheries.uvms.rules.service.exception.RulesServiceException;

@Stateless
public class EventServiceBean implements EventService {
    private final static Logger LOG = LoggerFactory.getLogger(EventServiceBean.class);

    @Inject
    @ErrorEvent
    Event<EventMessage> errorEvent;

    @EJB
    RulesMessageProducer producer;

    @EJB
    RulesService rulesService;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void pingReceived(@Observes @PingReceivedEvent EventMessage eventMessage) {
        try {
            PingResponse pingResponse = new PingResponse();
            pingResponse.setResponse("pong");
            String pingResponseText = JAXBMarshaller.marshallJaxBObjectToString(pingResponse);
            producer.sendModuleResponseMessage(eventMessage.getJmsMessage(), pingResponseText);
        } catch (RulesModelMarshallException | MessageException e) {
            LOG.error("[ Error when responding to ping. ] {}", e.getMessage());
            errorEvent.fire(eventMessage);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void setMovementReportReceived(@Observes @SetMovementReportReceivedEvent EventMessage message) {
        LOG.info("Validating movement from Exchange Module");
        try {
            RulesBaseRequest baseRequest = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), RulesBaseRequest.class);
            String username = baseRequest.getUsername();

            if (baseRequest.getMethod() != RulesModuleMethod.SET_MOVEMENT_REPORT) {
                String s = " [ Error, Set Movement Report invoked but it is not the intended method, caller is trying: "
                        + baseRequest.getMethod().name() + " ]";
                LOG.error(s);
                //sendAuditMessage(AuditObjectTypeEnum.CUSTOM_RULE_ACTION, AuditOperationEnum.CREATE, baseRequest.getMethod().name(), s, username);
            }

            SetMovementReportRequest request = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), SetMovementReportRequest.class);
            RawMovementType rawMovementType = request.getRequest();

            String pluginType = request.getType().name();

            rulesService.setMovementReportReceived(rawMovementType, pluginType, username);
        } catch (RulesModelMapperException | RulesServiceException e) {
            LOG.error("[ Error when creating movement ] {}", e.getMessage());
            //sendAuditMessage(AuditObjectTypeEnum.CUSTOM_RULE_ACTION, AuditOperationEnum.CREATE, RulesModuleMethod.SET_MOVEMENT_REPORT.name(), "Error when creating movement", "UVMS");
        }

    }


    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void getCustomRule(@Observes @GetCustomRuleReceivedEvent EventMessage message) {
        LOG.info("Get custom rule by guid");
        try {
            RulesBaseRequest baseRequest = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), RulesBaseRequest.class);

            if (baseRequest.getMethod() != RulesModuleMethod.GET_CUSTOM_RULE) {
                String s = " [ Error, Get Custom Rule invoked but it is not the intended method, caller is trying: "
                        + baseRequest.getMethod().name() + " ]";
                errorEvent.fire(new EventMessage(message.getJmsMessage(), ModuleResponseMapper.createFaultMessage(FaultCode.RULES_MESSAGE, s)));
            }

            GetCustomRuleRequest request = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), GetCustomRuleRequest.class);
            String ruleGuid = request.getGuid();

            CustomRuleType response = rulesService.getCustomRuleByGuid(ruleGuid);

            String responseString = RulesModuleResponseMapper.mapToGetCustomRuleResponse(response);
            producer.sendModuleResponseMessage(message.getJmsMessage(), responseString);
        } catch (RulesModelMapperException | RulesServiceException | RulesFaultException | MessageException e) {
            LOG.error("[ Error when fetching rule by guid ] {}", e.getMessage());
            errorEvent.fire(message);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void getTicketsByMovements(@Observes @GetTicketsByMovementsEvent EventMessage message) {
        LOG.info("Fetch tickets by movements");
        try {
            RulesBaseRequest baseRequest = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), RulesBaseRequest.class);

            if (baseRequest.getMethod() != RulesModuleMethod.GET_TICKETS_BY_MOVEMENTS) {
                String s = " [ Error, Get Tickets By Movements invoked but it is not the intended method, caller is trying: "
                        + baseRequest.getMethod().name() + " ]";
                errorEvent.fire(new EventMessage(message.getJmsMessage(), ModuleResponseMapper.createFaultMessage(FaultCode.RULES_MESSAGE, s)));
            }

            GetTicketsByMovementsRequest request = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), GetTicketsByMovementsRequest.class);
            List<String> movements = request.getMovementGuids();

            GetTicketListByMovementsResponse response = rulesService.getTicketsByMovements(movements);
            String responseString = RulesModuleResponseMapper.mapToGetTicketListByMovementsResponse(response.getTickets());
            producer.sendModuleResponseMessage(message.getJmsMessage(), responseString);
        } catch (RulesModelMapperException | RulesServiceException | RulesFaultException | MessageException e) {
            LOG.error("[ Error when fetching tickets by movements ] {}", e.getMessage());
            errorEvent.fire(message);
        }

    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void countTicketsByMovementsEvent(@Observes @CountTicketsByMovementsEvent EventMessage message) {
        LOG.info("Count tickets by movements");
        try {
            RulesBaseRequest baseRequest = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), RulesBaseRequest.class);

            if (baseRequest.getMethod() != RulesModuleMethod.COUNT_TICKETS_BY_MOVEMENTS) {
                String s = " [ Error, count tickets by movements invoked but it is not the intended method, caller is trying: "
                        + baseRequest.getMethod().name() + " ]";
                errorEvent.fire(new EventMessage(message.getJmsMessage(), ModuleResponseMapper.createFaultMessage(FaultCode.RULES_MESSAGE, s)));
            }

            CountTicketsByMovementsRequest request = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), CountTicketsByMovementsRequest.class);
            List<String> movements = request.getMovementGuids();

            long response = rulesService.countTicketsByMovements(movements);

            String responseString = RulesModuleResponseMapper.mapToCountTicketListByMovementsResponse(response);
            producer.sendModuleResponseMessage(message.getJmsMessage(), responseString);
        } catch (RulesModelMapperException | RulesServiceException | RulesFaultException | MessageException e) {
            LOG.error("[ Error when fetching ticket count by movements ] {}", e.getMessage());
            errorEvent.fire(message);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void getTicketsAndRulesByMovementsEvent(@Observes @GetTicketsAndRulesByMovementsEvent EventMessage message) {
        LOG.info("Fetch tickets and rules by movements");
        try {
            RulesBaseRequest baseRequest = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), RulesBaseRequest.class);

            if (baseRequest.getMethod() != RulesModuleMethod.GET_TICKETS_AND_RULES_BY_MOVEMENTS) {
                String s = " [ Error, Get Tickets And Rules By Movements invoked but it is not the intended method, caller is trying: "
                        + baseRequest.getMethod().name() + " ]";
                errorEvent.fire(new EventMessage(message.getJmsMessage(), ModuleResponseMapper.createFaultMessage(FaultCode.RULES_MESSAGE, s)));
            }

            GetTicketsAndRulesByMovementsRequest request = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), GetTicketsAndRulesByMovementsRequest.class);

            List<String> movements = request.getMovementGuids();

            GetTicketsAndRulesByMovementsResponse response = rulesService.getTicketsAndRulesByMovements(movements);
            String responseString = RulesModuleResponseMapper.getTicketsAndRulesByMovementsResponse(response.getTicketsAndRules());

            producer.sendModuleResponseMessage(message.getJmsMessage(), responseString);
        } catch (RulesModelMapperException | RulesServiceException | MessageException e) {
            LOG.error("[ Error when fetching tickets and rules by movements ] {}", e.getMessage());
            errorEvent.fire(message);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void SetFLUXFAReportMessageReceived(@Observes @SetFLUXFAReportMessageReceivedEvent EventMessage message) {
        try {
            LOG.info("get SetFLUXFAReportMessageReceived inside rules");
            RulesBaseRequest baseRequest = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), RulesBaseRequest.class);
            LOG.info("marshall RulesBaseRequest successful");
            SetFLUXFAReportMessageRequest request = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), SetFLUXFAReportMessageRequest.class);
            LOG.info("marshall SetFLUXFAReportMessageRequest successful");
            rulesService.setFLUXFAReportMessageReceived(request.getRequest(), request.getType().name(), request.getUsername());
        } catch (RulesModelMarshallException e) {
            LOG.error("[ Error when un marshalling RulesBaseRequest. ] {}", e);
        } catch (RulesServiceException e) {
            LOG.error("[ Error when sending FLUXFAReportMessage to rules. ] {}", e);
        }

    }
    
    public void setFLUXMDRSyncMessageReceivedEvent(@Observes @SetFLUXMDRSyncMessageReceivedEvent EventMessage message){
    	 try {
	    	 LOG.info("@SetFLUXMDRSyncMessageReceivedEvent recieved inside Rules Module.");
	         RulesBaseRequest baseRequest = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), RulesBaseRequest.class);
	         LOG.info("RulesBaseRequest Marshalling was successful. Method : "+baseRequest.getMethod());
	         SetFLUXMDRSyncMessageRulesRequest request = JAXBMarshaller.unmarshallTextMessage(message.getJmsMessage(), SetFLUXMDRSyncMessageRulesRequest.class);
	         LOG.info("SetFLUXMDRSyncMessageRequest Marshall was successful");
	         // Bypassing validation phase since it will probably be done in FLUX Module..
	         rulesService.mapAndSendFLUXMdrRequestMessageToExchange(request.getRequest());
    	 } catch (RulesModelMarshallException e) {
             LOG.error("[ Error when un marshalling RulesBaseRequest. ] {}", e);
         } 
    }

    @SuppressWarnings("unused")
	private void sendAuditMessage(AuditObjectTypeEnum type, AuditOperationEnum operation, String affectedObject, String comment, String username) {
        try {
            String message = AuditLogMapper.mapToAuditLog(type.getValue(), operation.getValue(), affectedObject, comment, username);
            producer.sendDataSourceMessage(message, DataSourceQueue.AUDIT);
        }
        catch (AuditModelMarshallException | MessageException e) {
            LOG.error("[ Error when sending message to Audit. ] {}", e.getMessage());
        }
    }

}
