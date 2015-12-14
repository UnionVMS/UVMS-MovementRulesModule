package eu.europa.ec.fisheries.uvms.rules.service.bean;

import eu.europa.ec.fisheries.schema.exchange.movement.v1.MovementType;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.RecipientInfoType;
import eu.europa.ec.fisheries.schema.exchange.plugin.types.v1.EmailType;
import eu.europa.ec.fisheries.schema.exchange.plugin.types.v1.PluginType;
import eu.europa.ec.fisheries.schema.rules.alarm.v1.AlarmItemType;
import eu.europa.ec.fisheries.schema.rules.alarm.v1.AlarmReportType;
import eu.europa.ec.fisheries.schema.rules.alarm.v1.AlarmStatusType;
import eu.europa.ec.fisheries.schema.rules.customrule.v1.ActionType;
import eu.europa.ec.fisheries.schema.rules.customrule.v1.CustomRuleType;
import eu.europa.ec.fisheries.schema.rules.customrule.v1.SubscriptionType;
import eu.europa.ec.fisheries.schema.rules.customrule.v1.SubscriptionTypeType;
import eu.europa.ec.fisheries.schema.rules.search.v1.CustomRuleQuery;
import eu.europa.ec.fisheries.schema.rules.source.v1.CreateAlarmReportResponse;
import eu.europa.ec.fisheries.schema.rules.source.v1.CreateTicketResponse;
import eu.europa.ec.fisheries.schema.rules.source.v1.GetCustomRuleListByQueryResponse;
import eu.europa.ec.fisheries.schema.rules.ticket.v1.TicketStatusType;
import eu.europa.ec.fisheries.schema.rules.ticket.v1.TicketType;
import eu.europa.ec.fisheries.uvms.audit.model.exception.AuditModelMarshallException;
import eu.europa.ec.fisheries.uvms.audit.model.mapper.AuditLogMapper;
import eu.europa.ec.fisheries.uvms.exchange.model.exception.ExchangeModelMapperException;
import eu.europa.ec.fisheries.uvms.exchange.model.mapper.ExchangeModuleRequestMapper;
import eu.europa.ec.fisheries.uvms.notifications.NotificationMessage;
import eu.europa.ec.fisheries.uvms.rules.message.constants.DataSourceQueue;
import eu.europa.ec.fisheries.uvms.rules.message.consumer.RulesResponseConsumer;
import eu.europa.ec.fisheries.uvms.rules.message.exception.MessageException;
import eu.europa.ec.fisheries.uvms.rules.message.producer.RulesMessageProducer;
import eu.europa.ec.fisheries.uvms.rules.model.constant.AuditObjectTypeEnum;
import eu.europa.ec.fisheries.uvms.rules.model.constant.AuditOperationEnum;
import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesFaultException;
import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesModelMapperException;
import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesModelMarshallException;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.JAXBMarshaller;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.RulesDataSourceRequestMapper;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.RulesDataSourceResponseMapper;
import eu.europa.ec.fisheries.uvms.rules.service.ValidationService;
import eu.europa.ec.fisheries.uvms.rules.service.business.MovementFact;
import eu.europa.ec.fisheries.uvms.rules.service.business.RawMovementFact;
import eu.europa.ec.fisheries.uvms.rules.service.business.RulesUtil;
import eu.europa.ec.fisheries.uvms.rules.service.event.AlarmReportCountEvent;
import eu.europa.ec.fisheries.uvms.rules.service.event.AlarmReportEvent;
import eu.europa.ec.fisheries.uvms.rules.service.event.TicketCountEvent;
import eu.europa.ec.fisheries.uvms.rules.service.event.TicketEvent;
import eu.europa.ec.fisheries.uvms.rules.service.exception.RulesServiceException;
import eu.europa.ec.fisheries.uvms.user.model.exception.ModelMarshallException;
import eu.europa.ec.fisheries.uvms.user.model.mapper.UserModuleRequestMapper;
import eu.europa.ec.fisheries.uvms.user.model.mapper.UserModuleResponseMapper;
import eu.europa.ec.fisheries.wsdl.user.module.FindOrganisationsResponse;
import eu.europa.ec.fisheries.wsdl.user.module.GetContactDetailResponse;
import eu.europa.ec.fisheries.wsdl.user.types.EndPoint;
import eu.europa.ec.fisheries.wsdl.user.types.Organisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.*;

@Stateless
public class ValidationServiceBean implements ValidationService {

    private final static Logger LOG = LoggerFactory.getLogger(ValidationServiceBean.class);

    @EJB
    RulesResponseConsumer consumer;

    @EJB
    RulesMessageProducer producer;

    @Inject
    @TicketEvent
    private Event<NotificationMessage> ticketEvent;

    @Inject
    @AlarmReportEvent
    private Event<NotificationMessage> alarmReportEvent;

    @Inject
    @AlarmReportCountEvent
    private Event<NotificationMessage> alarmReportCountEvent;

    @Inject
    @TicketCountEvent
    private Event<NotificationMessage> ticketCountEvent;

    /**
     * {@inheritDoc}
     *
     * @return
     * @throws RulesServiceException
     */
    @Override
    public List<CustomRuleType> getCustomRulesByUser(String userName) throws RulesServiceException, RulesFaultException {
        LOG.info("Get all custom rules invoked in service layer");
        try {
            String request = RulesDataSourceRequestMapper.mapGetCustomRulesByUser(userName);
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);
            return RulesDataSourceResponseMapper.mapToGetCustomRulesFromResponse(response, messageId);
        } catch (RulesModelMapperException | MessageException | JMSException e) {
            throw new RulesServiceException(e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return
     * @throws RulesServiceException
     */
    @Override
    public List<CustomRuleType> getRunnableCustomRules() throws RulesServiceException, RulesFaultException {
        LOG.info("Get all valid custom rules invoked in service layer");
        try {
            String request = RulesDataSourceRequestMapper.mapGetRunnableCustomRules();
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);
            return RulesDataSourceResponseMapper.mapToGetRunnableCustomRulesFromResponse(response, messageId);
        } catch (RulesModelMapperException | MessageException | JMSException e) {
            throw new RulesServiceException(e.getMessage());
        }
    }

    @Override
    public GetCustomRuleListByQueryResponse getCustomRulesByQuery(CustomRuleQuery query) throws RulesServiceException, RulesFaultException {
        LOG.info("Get custom rules by query invoked in service layer");
        try {
            String request = RulesDataSourceRequestMapper.mapCustomRuleListByQuery(query);
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);

            return RulesDataSourceResponseMapper.mapToCustomRuleListByQueryFromResponse(response, messageId);
        } catch (RulesModelMapperException | MessageException | JMSException e) {
            throw new RulesServiceException(e.getMessage());
        }
    }

    // Triggered by rule engine
    @Override
    public void customRuleTriggered(String ruleName, String ruleGuid, MovementFact fact, String actions) {
        LOG.info("Performing actions on triggered user rules");

        // Update last update
        updateLastTriggered(ruleGuid);

        // Always create a ticket
        createTicket(ruleName, ruleGuid, fact);

        sendMailToSubscribers(ruleGuid, ruleName, fact);

        // Actions list format:
        // ACTION,VALUE;ACTION,VALUE;
        // N.B! The .drl rule file gives the string "null" when (for instance)
        // value is null.
        String[] parsedActionKeyValueList = actions.split(";");
        for (String keyValue : parsedActionKeyValueList) {
            String[] keyValueList = keyValue.split(",");
            String action = keyValueList[0];
            String value = "";
            if (keyValueList.length == 2) {
                value = keyValueList[1];
            }
            switch (ActionType.valueOf(action)) {
                case EMAIL:
                    // Value=address.
                    sendToEmail(value, ruleName, fact);
                    break;
                case SEND_TO_ENDPOINT:
                    sendToEndpoint(ruleName, fact, value);
                    break;
//                case TICKET:
//                    createTicket(ruleName, ruleGuid, fact);
//                    break;

                /*
                case MANUAL_POLL:
                    LOG.info("NOT IMPLEMENTED!");
                    break;

                case ON_HOLD:
                    LOG.info("NOT IMPLEMENTED!");
                    break;
                case TOP_BAR_NOTIFICATION:
                    LOG.info("NOT IMPLEMENTED!");
                    break;
                case SMS:
                    LOG.info("NOT IMPLEMENTED!");
                    break;
                    */
                default:
                    LOG.info("The action '{}' is not defined", action);
                    break;
            }
        }
    }

    private void sendMailToSubscribers(String ruleGuid, String ruleName, MovementFact fact) {
        CustomRuleType customRuleType = null;
        try {
            // Get email subscribers
            String customRuleRequest = RulesDataSourceRequestMapper.mapGetCustomRule(ruleGuid);
            String customRuleMessageId = producer.sendDataSourceMessage(customRuleRequest, DataSourceQueue.INTERNAL);
            TextMessage customRuleMessage = consumer.getMessage(customRuleMessageId, TextMessage.class);
            customRuleType = RulesDataSourceResponseMapper.getCustomRuleResponse(customRuleMessage, customRuleMessageId);
        } catch (RulesModelMapperException | RulesFaultException | MessageException | JMSException e) {
            LOG.error("[ Failed to fetch rule when sending email to subscribers! {} ]", e.getMessage());
        }

        List<SubscriptionType> subscriptions = customRuleType.getSubscriptions();
        for (SubscriptionType subscription : subscriptions) {
            if (SubscriptionTypeType.EMAIL.equals(subscription.getType())) {
                try {
                    // Find current email address
                    String userRequest = UserModuleRequestMapper.mapToGetContactDetailsRequest(subscription.getOwner());
                    String userMessageId = producer.sendDataSourceMessage(userRequest, DataSourceQueue.USER);
                    TextMessage userMessage = consumer.getMessage(userMessageId, TextMessage.class);
                    GetContactDetailResponse userResponse = JAXBMarshaller.unmarshallTextMessage(userMessage, GetContactDetailResponse.class);

                    String emailAddress = userResponse.getContactDetails().getEMail();

                    sendToEmail(emailAddress, ruleName, fact);
                } catch (Exception e) {
                    // If a mail attempt fails, proceed with the rest
                    LOG.error("Could not send email to user '{}'", subscription.getOwner());
                }
            }
        }
    }

    private void updateLastTriggered(String ruleGuid) {
        try {
            String request = RulesDataSourceRequestMapper.mapUpdateCustomRuleLastTriggered(ruleGuid);
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);
        } catch (RulesModelMapperException | MessageException e) {
            LOG.warn("[ Failed to update last triggered date for rule {} ]", ruleGuid);
        }

    }

// TODO: This is unused and should probably be deleted
/*
    @Override
    public void sendToEndpoint(eu.europa.ec.fisheries.schema.movement.v1.MovementType createdMovement, String countryCode) throws MessageException, ExchangeModelMapperException {
        if (createdMovement.getMetaData() != null) {
            List<MovementMetaDataAreaType> areas = createdMovement.getMetaData().getAreas();
            MovementType exchangeMovement = RulesDozerMapper.getInstance().getMapper().map(createdMovement, MovementType.class);

            for (MovementMetaDataAreaType area : areas) {
                String ruleName = "Automatic Forwarding Rule";

                XMLGregorianCalendar date = null;
                try {
                    GregorianCalendar c = new GregorianCalendar();
                    c.setTime(new Date());
                    date = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
                } catch (DatatypeConfigurationException e) {
                    e.printStackTrace();
                }

                if ("EEZ".equals(area.getAreaType()) || "RFMO".equals(area.getAreaType())) {
                    String destination = area.getCode();

                    // Make sure you don't send to flag state since it already has it (it this one that we are forwarding here)
                    if (!countryCode.equals(destination)) {
                        LOG.info("Forwarding movement '{}' to {}", exchangeMovement.getGuid(), destination);
                        String request = ExchangeModuleRequestMapper.createSendReportToPlugin(null, PluginType.FLUX, date, ruleName, destination, exchangeMovement);
                        String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.EXCHANGE);
                        TextMessage response = consumer.getMessage(messageId, TextMessage.class);

                        // TODO: Do something with the response
                    }
                }
            }
        }
    }
*/

    private void sendToEndpoint(String ruleName, MovementFact fact, String endpoint) {
        LOG.info("Sending to endpoint '{}'", endpoint);

        try {
            MovementType exchangeMovement = fact.getExchangeMovement();

            XMLGregorianCalendar date = RulesUtil.dateToXmlGregorian(new Date());

            String userRequest = UserModuleRequestMapper.mapToFindOrganisationsRequest(endpoint);
            String userMessageId = producer.sendDataSourceMessage(userRequest, DataSourceQueue.USER);
            TextMessage userMessage = consumer.getMessage(userMessageId, TextMessage.class);
            FindOrganisationsResponse userResponse = JAXBMarshaller.unmarshallTextMessage(userMessage, FindOrganisationsResponse.class);

            List<RecipientInfoType> recipientInfoList = new ArrayList<>();

            List<Organisation> organisations = userResponse.getOrganisation();
            for (Organisation organisation : organisations) {
                List<EndPoint> endPoints = organisation.getEndPoints();
                for (EndPoint endPoint : endPoints) {
                    RecipientInfoType recipientInfo = new RecipientInfoType();
                    recipientInfo.setKey(endPoint.getName());
                    recipientInfo.setValue(endPoint.getUri());
                    recipientInfoList.add(recipientInfo);
                }
            }

            String exchangeRequest = ExchangeModuleRequestMapper.createSendReportToPlugin(null, PluginType.FLUX, date, ruleName, endpoint, exchangeMovement, recipientInfoList, fact.getVesselName(), fact.getVesselIrcs());
            String messageId = producer.sendDataSourceMessage(exchangeRequest, DataSourceQueue.EXCHANGE);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);

            sendAuditMessage(AuditObjectTypeEnum.CUSTOM_RULE_ACTION, AuditOperationEnum.SEND_TO_ENDPOINT, endpoint);

            // TODO: Do something with the response???

        } catch (ExchangeModelMapperException | MessageException | DatatypeConfigurationException | ModelMarshallException | RulesModelMarshallException e) {
            LOG.error("[ Failed to send to endpoint! {} ]", e.getMessage());
        }

    }

    private void sendToEmail(String emailAddress, String ruleName, MovementFact fact) {
        // TODO: Decide on what message to send

        LOG.info("Sending email to '{}'", emailAddress);

        EmailType email = new EmailType();

        email.setSubject(buildSubject(ruleName));
        email.setBody(buildBody(ruleName, fact));
        email.setTo(emailAddress);

        // TODO: Hard coded...
        String pluginName = "eu.europa.ec.fisheries.uvms.plugins.sweagencyemail";
        try {
            String request = ExchangeModuleRequestMapper.createSetCommandSendEmailRequest(pluginName, email);
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.EXCHANGE);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);

            sendAuditMessage(AuditObjectTypeEnum.CUSTOM_RULE_ACTION, AuditOperationEnum.SEND_EMAIL, emailAddress);

            // TODO: Do something with the response???
//            xxx = ExchangeModuleResponseMapper.mapSetCommandSendEmailResponse(response);

//            ExchangeModuleResponseMapper.mapSetCommandResponse(response);
//            f�r att f� ut AcknowledgeType f�r hur det gick med Email-et.
// Metoden kastar ExchangeValidationException (som �r en ExchangeModelMapperException) - som containar "ExchangeFault.code - och ExchangeFault.message" som message om det �r ett Fault, ger AcknowledgeType.OK om det gick bra AcknowledgeType.NOK om pluginen inte �r startad

        } catch (ExchangeModelMapperException | MessageException e) {
            LOG.error("[ Failed to send email! {} ]", e.getMessage());
        }
    }

    private String buildSubject(String ruleName) {
        StringBuilder subjectBuilder = new StringBuilder();
        subjectBuilder.append("Rule '")
                .append(ruleName)
                .append("' has been triggered.");
        return subjectBuilder.toString();
    }

    private String buildBody(String ruleName, MovementFact fact) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>")
                .append(buildSubject(ruleName))
                .append("<br><br>")
                .append(buildAssetBodyPart(fact))
                .append(buildPositionBodyPart(fact))
                .append("</html>");

        return sb.toString();
    }

    private String buildAssetBodyPart(MovementFact fact) {
        StringBuilder assetBuilder = new StringBuilder();
        assetBuilder.append("<b>Asset:</b>")
                .append("<br>&nbsp;&nbsp;")
                .append("Name: ")
                .append(fact.getVesselName())
                .append("<br>&nbsp;&nbsp;")
                .append("IRCS: ")
                .append(fact.getVesselIrcs())
                .append("<br>&nbsp;&nbsp;")
                .append("CFR: ")
                .append(fact.getVesselCfr())
                .append("<br>");

        return assetBuilder.toString();
    }

    private String buildPositionBodyPart(MovementFact fact) {
        StringBuilder positionBuilder = new StringBuilder();
        positionBuilder.append("<b>Position report:</b>")
                .append("<br>&nbsp;&nbsp;")
                .append("Report timestamp: ")
                .append(fact.getPositionTime())
                .append("<br>&nbsp;&nbsp;")
                .append("Longitude: ")
                .append(fact.getLongitude())
                .append("<br>&nbsp;&nbsp;")
                .append("Latitude: ")
                .append(fact.getLatitude())
                .append("<br>&nbsp;&nbsp;")
                .append("Status code: ")
                .append(fact.getStatusCode())
                .append("<br>&nbsp;&nbsp;")
                .append("Reported speed: ")
                .append(fact.getReportedSpeed())
                .append("<br>&nbsp;&nbsp;")
                .append("Reported course: ")
                .append(fact.getReportedCourse())
                .append("<br>&nbsp;&nbsp;")
                .append("Calculated speed: ")
                .append(fact.getCalculatedSpeed())
                .append("<br>&nbsp;&nbsp;")
                .append("Calculated course: ")
                .append(fact.getCalculatedCourse())
                .append("<br>&nbsp;&nbsp;")
                .append("Com channel type: ")
                .append(fact.getComChannelType())
                .append("<br>&nbsp;&nbsp;")
                .append("Segment type: ")
                .append(fact.getSegmentType())
                .append("<br>&nbsp;&nbsp;")
                .append("Source: ")
                .append(fact.getSource())
                .append("<br>&nbsp;&nbsp;")
                .append("Movement type: ")
                .append(fact.getMovementType())
                .append("<br>&nbsp;&nbsp;")
                .append("Activity type: ")
                .append(fact.getActivityMessageType())
                .append("<br>&nbsp;&nbsp;")
                .append("Closest port: ")
                .append(fact.getClosestPortCode())
                .append("<br>&nbsp;&nbsp;")
                .append("Closest country: ")
                .append(fact.getClosestCountryCode())
                .append("<br>&nbsp;&nbsp;");

        positionBuilder.append("Areas:");
        for (int i = 0; i < fact.getAreaCodes().size(); i++) {
            positionBuilder.append("<br>&nbsp;&nbsp;&nbsp;&nbsp;")
                    .append(fact.getAreaCodes().get(i))
                    .append(" (")
                    .append(fact.getAreaTypes().get(i))
                    .append(")");
        }

        return positionBuilder.toString();
    }

    private void createTicket(String ruleName, String ruleGuid, MovementFact fact) {
        LOG.info("Create ticket invoked in service layer");
        try {
            TicketType ticket = new TicketType();

            ticket.setVesselGuid(fact.getVesselGuid());
            ticket.setOpenDate(RulesUtil.dateToString(new Date()));
            ticket.setRuleName(ruleName);
            ticket.setRuleGuid(ruleGuid);
            ticket.setStatus(TicketStatusType.OPEN);
            ticket.setUpdatedBy("UVMS");
            ticket.setMovementGuid(fact.getMovementGuid());
            ticket.setGuid(UUID.randomUUID().toString());

            for (int i = 0; i < fact.getAreaTypes().size(); i++) {
                if ("EEZ".equals(fact.getAreaTypes().get(i))) {
                    ticket.setRecipient(fact.getAreaCodes().get(i));
                }
            }

            String request = RulesDataSourceRequestMapper.mapCreateTicket(ticket);
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);

            // Notify long-polling clients of the new ticket
            CreateTicketResponse createTicketResponse = JAXBMarshaller.unmarshallTextMessage(response, CreateTicketResponse.class);
            ticketEvent.fire(new NotificationMessage("guid", createTicketResponse.getTicket().getGuid()));

            // Notify long-polling clients of the change (no vlaue since FE will need to fetch it)
            ticketCountEvent.fire(new NotificationMessage("ticketCount", null));

            sendAuditMessage(AuditObjectTypeEnum.TICKET, AuditOperationEnum.CREATE, createTicketResponse.getTicket().getGuid());
        } catch (RulesModelMapperException | MessageException e) {
            LOG.error("[ Failed to create ticket! {} ]", e.getMessage());
        }
    }

    // Triggered by rule engine
    @Override
    public void createAlarmReport(String ruleName, RawMovementFact fact) {
        LOG.info("Create alarm invoked in validation service");
        try {
            // TODO: Decide who sets the guid, Rules or Exchange
            if (fact.getRawMovementType().getGuid() == null) {
                fact.getRawMovementType().setGuid(UUID.randomUUID().toString());
            }

            AlarmReportType alarmReport = new AlarmReportType();
            alarmReport.setOpenDate(RulesUtil.dateToString(new Date()));
            alarmReport.setStatus(AlarmStatusType.OPEN);
            alarmReport.setRawMovement(fact.getRawMovementType());
            alarmReport.setUpdatedBy("UVMS");
            alarmReport.setPluginType(fact.getPluginType());
            alarmReport.setVesselGuid(fact.getVesselGuid());
            alarmReport.setInactivatePosition(false);

            // TODO: Add sender, recipient and assetGuid

            // Alarm item
            List<AlarmItemType> alarmItems = new ArrayList<>();
            AlarmItemType alarmItem = new AlarmItemType();
            alarmItem.setGuid(UUID.randomUUID().toString());
            alarmItem.setRuleGuid("sanity rule - " + ruleName);
            alarmItem.setRuleName(ruleName);
            alarmItems.add(alarmItem);
            alarmReport.getAlarmItem().addAll(alarmItems);

            String request = RulesDataSourceRequestMapper.mapCreateAlarmReport(alarmReport);
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);

            // Notify long-polling clients of the new alarm report
            CreateAlarmReportResponse createAlarmResponse = JAXBMarshaller.unmarshallTextMessage(response, CreateAlarmReportResponse.class);
            alarmReportEvent.fire(new NotificationMessage("guid", createAlarmResponse.getAlarm().getGuid()));

            // Notify long-polling clients of the change (no vlaue since FE will need to fetch it)
            alarmReportCountEvent.fire(new NotificationMessage("alarmCount", null));

            sendAuditMessage(AuditObjectTypeEnum.ALARM, AuditOperationEnum.CREATE, createAlarmResponse.getAlarm().getGuid());
        } catch (RulesModelMapperException | MessageException e) {
            LOG.error("[ Failed to create alarm! {} ]", e.getMessage());
        }
    }

    @Override
    public long getNumberOfOpenAlarmReports() throws RulesServiceException, RulesFaultException {
        try {
            String request = RulesDataSourceRequestMapper.getNumberOfOpenAlarmReports();
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);

            return RulesDataSourceResponseMapper.mapGetNumberOfOpenAlarmReportsFromResponse(response, messageId);
        } catch (MessageException | JMSException | RulesModelMapperException e) {
            LOG.error("[ Error when getting number of open alarms ] {}", e.getMessage());
            throw new RulesServiceException("[ Error when getting number of open alarms. ]");
        }
    }

    @Override
    public long getNumberOfOpenTickets() throws RulesServiceException, RulesFaultException {
        try {
            String request = RulesDataSourceRequestMapper.getNumberOfOpenTickets();
            String messageId = producer.sendDataSourceMessage(request, DataSourceQueue.INTERNAL);
            TextMessage response = consumer.getMessage(messageId, TextMessage.class);

            return RulesDataSourceResponseMapper.mapGetNumberOfOpenTicketsFromResponse(response, messageId);
        } catch (MessageException | JMSException | RulesModelMapperException e) {
            LOG.error("[ Error when getting number of open tickets ] {}", e.getMessage());
            throw new RulesServiceException("[ Error when getting number of open alarms. ]");
        }
    }

    private void sendAuditMessage(AuditObjectTypeEnum type, AuditOperationEnum operation, String affectedObject) {
        try {
            String message = AuditLogMapper.mapToAuditLog(type.getValue(), operation.getValue(), affectedObject);
            producer.sendDataSourceMessage(message, DataSourceQueue.AUDIT);
        }
        catch (AuditModelMarshallException | MessageException e) {
            LOG.error("[ Error when sending message to Audit. ] {}", e.getMessage());
        }
    }
}
