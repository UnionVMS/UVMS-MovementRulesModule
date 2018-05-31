package eu.europa.ec.fisheries.uvms.rules.message.consumer.bean;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import java.util.Date;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;
import eu.europa.ec.fisheries.schema.exchange.module.v1.ProcessedMovementResponse;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.MovementBaseType;
import eu.europa.ec.fisheries.schema.exchange.movement.v1.MovementRefTypeType;
import eu.europa.ec.fisheries.schema.rules.exchange.v1.PluginType;
import eu.europa.ec.fisheries.schema.rules.module.v1.PingRequest;
import eu.europa.ec.fisheries.schema.rules.module.v1.PingResponse;
import eu.europa.ec.fisheries.schema.rules.module.v1.RulesModuleMethod;
import eu.europa.ec.fisheries.schema.rules.movement.v1.RawMovementType;
import eu.europa.ec.fisheries.uvms.rules.message.AbstractMessageTest;
import eu.europa.ec.fisheries.uvms.rules.message.JMSHelper;
import eu.europa.ec.fisheries.uvms.rules.message.TestHelper;
import eu.europa.ec.fisheries.uvms.rules.message.event.carrier.EventMessage;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.JAXBMarshaller;
import eu.europa.ec.fisheries.uvms.rules.model.mapper.RulesModuleRequestMapper;

@RunAsClient
@RunWith(Arquillian.class)
public class RulesEventMessageConsumerBeanTest extends AbstractMessageTest {

    private JMSHelper jmsHelper = new JMSHelper();
    
    @Test
    public void pingTest() throws Exception {
        PingRequest request = new PingRequest();
        request.setMethod(RulesModuleMethod.PING);
        String requestString = JAXBMarshaller.marshallJaxBObjectToString(request);
        String correlationId = jmsHelper.sendMessageToRules(requestString);
        Message message = jmsHelper.listenForResponse(correlationId);
        assertThat(message, is(notNullValue()));
        
        PingResponse pingResponse = JAXBMarshaller.unmarshallTextMessage((TextMessage) message, PingResponse.class);
        assertThat(pingResponse.getResponse(), is("pong"));
    }
    
    @Test
    public void setMovementReportTest() throws Exception {
        RawMovementType movement = TestHelper.createBasicMovement();
        String request = RulesModuleRequestMapper.createSetMovementReportRequest(PluginType.NAF, movement, "testUser");
        String correlationId = jmsHelper.sendMessageToRules(request);
        Message responseMessage = jmsHelper.listenForResponseOnQueue(correlationId, "UVMSExchangeEvent");
        
        ProcessedMovementResponse movementResponse = JAXBMarshaller.unmarshallTextMessage((TextMessage) responseMessage, ProcessedMovementResponse.class);
        assertThat(movementResponse.getMovementRefType().getType(), is(MovementRefTypeType.MOVEMENT));
        
        MovementBaseType returnedMovement = movementResponse.getOrgRequest().getMovement();        
        assertThat(returnedMovement.getAssetId().getAssetIdList().get(0).getValue(), is(movement.getAssetId().getAssetIdList().get(0).getValue()));
    }
    
    @Test
    public void setMovementReportCreateTwoPositionsTest() throws Exception {
        RawMovementType movement = TestHelper.createBasicMovement();
        String request = RulesModuleRequestMapper.createSetMovementReportRequest(PluginType.NAF, movement, "testUser");
        String correlationId = jmsHelper.sendMessageToRules(request);
        Message responseMessage = jmsHelper.listenForResponseOnQueue(correlationId, "UVMSExchangeEvent");
        
        ProcessedMovementResponse movementResponse = JAXBMarshaller.unmarshallTextMessage((TextMessage) responseMessage, ProcessedMovementResponse.class);
        MovementBaseType returnedMovement = movementResponse.getOrgRequest().getMovement();
        
        assertThat(returnedMovement.getAssetId().getAssetIdList().get(0).getValue(), is(movement.getAssetId().getAssetIdList().get(0).getValue()));
        
        RawMovementType movement2 = TestHelper.createBasicMovement();
        request = RulesModuleRequestMapper.createSetMovementReportRequest(PluginType.NAF, movement2, "testUser");
        correlationId = jmsHelper.sendMessageToRules(request);
        responseMessage = jmsHelper.listenForResponseOnQueue(correlationId, "UVMSExchangeEvent");
        
        ProcessedMovementResponse movementResponse2 = JAXBMarshaller.unmarshallTextMessage((TextMessage) responseMessage, ProcessedMovementResponse.class);
        MovementBaseType returnedMovement2 = movementResponse2.getOrgRequest().getMovement();
        
        assertThat(returnedMovement2.getAssetId().getAssetIdList().get(0).getValue(), is(movement2.getAssetId().getAssetIdList().get(0).getValue()));
    }
    
    @Test
    public void setMovementReportNullLatitudeShouldTriggerSanityRuleTest() throws Exception {
        RawMovementType movement = TestHelper.createBasicMovement();
        movement.getPosition().setLatitude(null);
        String request = RulesModuleRequestMapper.createSetMovementReportRequest(PluginType.NAF, movement, "testUser");
        String correlationId = jmsHelper.sendMessageToRules(request);
        Message responseMessage = jmsHelper.listenForResponseOnQueue(correlationId, "UVMSExchangeEvent");
        
        ProcessedMovementResponse movementResponse = JAXBMarshaller.unmarshallTextMessage((TextMessage) responseMessage, ProcessedMovementResponse.class);
        assertThat(movementResponse.getMovementRefType().getType(), is(MovementRefTypeType.ALARM));
    }
    
    @Test
    public void setMovementReportFutureDateShouldTriggerSanityRuleTest() throws Exception {
        RawMovementType movement = TestHelper.createBasicMovement();
        movement.setPositionTime(new Date(new Date().getTime() + 100000));
        String request = RulesModuleRequestMapper.createSetMovementReportRequest(PluginType.NAF, movement, "testUser");
        String correlationId = jmsHelper.sendMessageToRules(request);
        Message responseMessage = jmsHelper.listenForResponseOnQueue(correlationId, "UVMSExchangeEvent");
        
        ProcessedMovementResponse movementResponse = JAXBMarshaller.unmarshallTextMessage((TextMessage) responseMessage, ProcessedMovementResponse.class);
        assertThat(movementResponse.getMovementRefType().getType(), is(MovementRefTypeType.ALARM));
    }
    
    /*
    @Test
    @RunAsClient
    public void getTicketsAndRulesByMovementsTest() throws Exception {
        String request = RulesModuleRequestMapper.createGetTicketsAndRulesByMovementsRequest(new ArrayList<String>()); 
        String correlationId = jmsHelper.sendMessageToRules(request);
        Message response = jmsHelper.listenForResponse(correlationId);
        assertThat(response, is(notNullValue()));
    }
    */
}
