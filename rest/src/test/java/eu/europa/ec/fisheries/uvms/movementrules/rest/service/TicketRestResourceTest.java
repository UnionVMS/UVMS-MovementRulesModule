package eu.europa.ec.fisheries.uvms.movementrules.rest.service;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.util.*;
import javax.inject.Inject;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import eu.europa.ec.fisheries.schema.movementrules.customrule.v1.*;
import eu.europa.ec.fisheries.uvms.movementrules.service.business.RulesUtil;
import eu.europa.ec.fisheries.uvms.movementrules.service.constants.ServiceConstants;
import eu.europa.ec.fisheries.uvms.movementrules.service.dao.RulesDao;
import eu.europa.ec.fisheries.uvms.movementrules.service.entity.CustomRule;
import eu.europa.ec.fisheries.uvms.movementrules.service.entity.RuleSubscription;
import eu.europa.ec.fisheries.uvms.movementrules.service.entity.Ticket;
import eu.europa.ec.fisheries.uvms.movementrules.service.mapper.CustomRuleMapper;
import eu.europa.ec.fisheries.uvms.movementrules.service.mapper.TicketMapper;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.europa.ec.fisheries.schema.movementrules.module.v1.GetTicketListByMovementsResponse;
import eu.europa.ec.fisheries.schema.movementrules.module.v1.GetTicketListByQueryResponse;
import eu.europa.ec.fisheries.schema.movementrules.search.v1.ListPagination;
import eu.europa.ec.fisheries.schema.movementrules.search.v1.TicketListCriteria;
import eu.europa.ec.fisheries.schema.movementrules.search.v1.TicketQuery;
import eu.europa.ec.fisheries.schema.movementrules.search.v1.TicketSearchKey;
import eu.europa.ec.fisheries.schema.movementrules.ticket.v1.TicketStatusType;
import eu.europa.ec.fisheries.schema.movementrules.ticket.v1.TicketType;
import eu.europa.ec.fisheries.uvms.movementrules.rest.service.arquillian.BuildRulesRestDeployment;

//@RunAsClient
@RunWith(Arquillian.class)
public class TicketRestResourceTest extends BuildRulesRestDeployment {

    @Inject
    RulesDao rulesDao;

    @Test
    public void getTicketListTest() throws Exception {
        TicketQuery query = getBasicTicketQuery();
        TicketListCriteria criteria = new TicketListCriteria();
        criteria.setKey(TicketSearchKey.RULE_NAME);
        criteria.setValue("Test Name");
        query.getTicketSearchCriteria().add(criteria);
        
        String response = getWebTarget()
                .path("tickets/list/" + "testUser")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(query), String.class);
        
        GetTicketListByQueryResponse ticketList = deserializeResponseDto(response, GetTicketListByQueryResponse.class);
        assertNotNull(ticketList.getTickets());
        int numberOfTickets = ticketList.getTickets().size();

        Ticket ticket = getCompleteTicket();


        CustomRule customRule = CustomRuleMapper.toCustomRuleEntity(getCompleteNewCustomRule());
        customRule.setGuid(UUID.randomUUID().toString());
        customRule.setAvailability(AvailabilityType.GLOBAL);
        customRule.setUpdatedBy("testUser");
        RuleSubscription ruleSubscription = new RuleSubscription();
        ruleSubscription.setType(SubscriptionTypeType.TICKET);
        ruleSubscription.setCustomRule(customRule);
        ruleSubscription.setOwner("testUser");
        customRule.getRuleSubscriptionList().add(ruleSubscription);
        customRule = rulesDao.createCustomRule(customRule);

        ticket.setRuleName(customRule.getName());
        ticket.setRuleGuid(customRule.getGuid());
        ticket.setUpdatedBy("testUser");

        Ticket createdTicket = rulesDao.createTicket(ticket);
        criteria.setValue(customRule.getName());
        response = getWebTarget()
                .path("tickets/list/" + "testUser")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(query), String.class);

        ticketList = deserializeResponseDto(response, GetTicketListByQueryResponse.class);
        assertThat(ticketList.getTickets().size(), is(numberOfTickets + 1));

        rulesDao.removeTicketAfterTests(createdTicket);
        rulesDao.removeCustomRuleAfterTests(customRule);
    }

    @Test
    public void negativeGetTicketListTest() throws Exception{
        String response = getWebTarget()
                .path("tickets/list/" + "testUser")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(new TicketQuery()), String.class);

        assertEquals(500, getReturnCode(response));
    }
    
    @Test
    public void getTicketsByMovementsTest() throws Exception {
        String response = getWebTarget()
                .path("tickets/listByMovements")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Arrays.asList("TEST_GUID")), String.class);
        
        GetTicketListByMovementsResponse ticketList = deserializeResponseDto(response, GetTicketListByMovementsResponse.class);
        assertThat(ticketList.getTickets().size(), is(0));

        response = getWebTarget()
                .path("tickets/listByMovements")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Arrays.asList("movement Guid")), String.class);

        ticketList = deserializeResponseDto(response, GetTicketListByMovementsResponse.class);
        assertNotNull(ticketList.getTickets());
        int numberOfPriorTickets = ticketList.getTickets().size();


        Ticket ticket = getCompleteTicket();
        ticket.setMovementGuid("movement Guid");
        ticket = rulesDao.createTicket(ticket);

        response = getWebTarget()
                .path("tickets/listByMovements")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Arrays.asList("movement Guid")), String.class);
        ticketList = deserializeResponseDto(response, GetTicketListByMovementsResponse.class);
        assertEquals(ticketList.getTickets().size(), numberOfPriorTickets + 1);

        rulesDao.removeTicketAfterTests(ticket);
    }

    @Test
    public void countTicketsByMovementsTest() throws Exception {
        String response = getWebTarget()
                .path("tickets/countByMovements")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Arrays.asList("TEST_GUID")), String.class);
        
        Long ticketList = deserializeResponseDto(response, Long.class);
        assertThat(ticketList, is(0L));

        response = getWebTarget()
                .path("tickets/countByMovements")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Arrays.asList("movement Guid")), String.class);

        ticketList = deserializeResponseDto(response, Long.class);
        assertNotNull(ticketList);
        int numberOfPriorTickets = ticketList.intValue();


        Ticket ticket = getCompleteTicket();
        ticket.setMovementGuid("movement Guid");
        ticket = rulesDao.createTicket(ticket);

        response = getWebTarget()
                .path("tickets/countByMovements")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(Arrays.asList("movement Guid")), String.class);
        ticketList = deserializeResponseDto(response, Long.class);
        assertEquals(ticketList.intValue(), numberOfPriorTickets + 1);

        rulesDao.removeTicketAfterTests(ticket);
    }
    
    @Test
    public void negativeUpdateTicketStatusTest() throws Exception{ //there are no tickets to update so this one will result in an internal server error
        TicketType ticketType = new TicketType();
        ticketType.setGuid("TestGuid");


        String response = getWebTarget()
                .path("tickets/status")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(ticketType), String.class);
        assertEquals(500 ,getReturnCode(response));


    }

    @Test
    public void updateTicketStatusTest() throws Exception{
        Ticket ticket = getCompleteTicket();
        ticket.setStatus(TicketStatusType.OPEN);
        ticket = rulesDao.createTicket(ticket);
        TicketType ticketType = TicketMapper.toTicketType(ticket);
        ticketType.setStatus(TicketStatusType.CLOSED);

        String response = getWebTarget()
                .path("tickets/status")
                .request(MediaType.APPLICATION_JSON)
                .put(Entity.json(ticketType), String.class);
        TicketType responseTicket = deserializeResponseDto(response, TicketType.class);


        assertEquals(TicketStatusType.CLOSED, responseTicket.getStatus());

        rulesDao.removeTicketAfterTests(ticket);
    }
    
    @Test
    public void updateTicketStatusByQueryTest() throws Exception {
        TicketQuery ticketQuery = new TicketQuery();
        TicketListCriteria tlc = new TicketListCriteria();
        tlc.setKey(TicketSearchKey.TICKET_GUID);
        tlc.setValue("Test Guid");
        ticketQuery.getTicketSearchCriteria().add(tlc);
        ListPagination lp = new ListPagination();
        lp.setPage(1);
        lp.setListSize(10);
        ticketQuery.setPagination(lp);


        String response = getWebTarget()
                    .path("tickets/status/vms_admin_com/" + TicketStatusType.OPEN)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(ticketQuery), String.class);

        //ObjectMapper objectMapper = new ObjectMapper();
        //why are we treating this as an array??????
        //Object[] returnArray = deserializeResponseDto(response, Object[].class);
        //I have no idea why but for some reason the response gets deserialized as a hashmap instead of a ticketType, so bring out the ugly hack......
        //List<TicketType> responseList = deserializeResponseDto(response, List.class);
        List<HashMap> responseList = deserializeResponseDto(response, List.class);
        assertThat(responseList.size(), is(0));

        Date now = new Date(System.currentTimeMillis() - 1000);
        tlc.setKey(TicketSearchKey.FROM_DATE);
        tlc.setValue(RulesUtil.dateToString(now));

        CustomRule customRule = CustomRuleMapper.toCustomRuleEntity(getCompleteNewCustomRule());
        customRule.setGuid(UUID.randomUUID().toString());
        customRule.setAvailability(AvailabilityType.GLOBAL);
        customRule.setUpdatedBy("vms_admin_com");
        RuleSubscription ruleSubscription = new RuleSubscription();
        ruleSubscription.setType(SubscriptionTypeType.TICKET);
        ruleSubscription.setCustomRule(customRule);
        ruleSubscription.setOwner("vms_admin_com");
        customRule.getRuleSubscriptionList().add(ruleSubscription);
        customRule = rulesDao.createCustomRule(customRule);

        Ticket ticket1 = getCompleteTicket();
        ticket1.setRuleGuid(customRule.getGuid());
        Ticket ticket2 = getCompleteTicket();
        ticket2.setRuleGuid(customRule.getGuid());
        ticket1 = rulesDao.createTicket(ticket1);
        ticket2 = rulesDao.createTicket(ticket2);

        response = getWebTarget()
                .path("tickets/status/vms_admin_com/" + TicketStatusType.CLOSED)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(ticketQuery), String.class);

        //ObjectMapper objectMapper = new ObjectMapper();
        responseList = deserializeResponseDto(response, ArrayList.class);
        assertEquals(2 ,responseList.size());
        //Why is it treating this as a hash map???????
        for(HashMap ticketType : responseList){
            assertEquals(TicketStatusType.CLOSED.value(), ticketType.get("status"));
        }

        rulesDao.removeTicketAfterTests(ticket1);
        rulesDao.removeTicketAfterTests(ticket2);
        rulesDao.removeCustomRuleAfterTests(customRule);
    }

    @Test
    public void getTicketByGuid() throws Exception {
        String response = getWebTarget()
                .path("tickets/" + "TestGuid")    //no tickets in the db
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);
        assertEquals(500, getReturnCode(response));


        Ticket ticket = getCompleteTicket();
        ticket = rulesDao.createTicket(ticket);

        response = getWebTarget()
                .path("tickets/" + ticket.getGuid())    //no tickets in the db
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);

        TicketType responseTicket = deserializeResponseDto(response, TicketType.class);
        assertNotNull(responseTicket);
        assertEquals(ticket.getGuid(), responseTicket.getGuid());

        rulesDao.removeTicketAfterTests(ticket);
    }

    @Test
    public void getNumberOfOpenTicketReportsTest() throws Exception{
        String response = getWebTarget()
                .path("/tickets/countopen/" + "ShouldBeEmpty")    //no tickets in the db
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);

        Long ticketList = deserializeResponseDto(response, Long.class);
        assertEquals(0 , ticketList.intValue());

        CustomRule customRule = CustomRuleMapper.toCustomRuleEntity(getCompleteNewCustomRule());
        customRule.setGuid(UUID.randomUUID().toString());
        customRule.setAvailability(AvailabilityType.GLOBAL);
        customRule.setUpdatedBy("TestUser");
        RuleSubscription ruleSubscription = new RuleSubscription();
        ruleSubscription.setType(SubscriptionTypeType.TICKET);
        ruleSubscription.setCustomRule(customRule);
        ruleSubscription.setOwner("TestUser");
        customRule.getRuleSubscriptionList().add(ruleSubscription);
        customRule = rulesDao.createCustomRule(customRule);

        Ticket ticket = getCompleteTicket();
        ticket.setRuleGuid(customRule.getGuid());
        ticket.setUpdatedBy("TestUser");
        response = getWebTarget()
                .path("/tickets/countopen/" + ticket.getUpdatedBy())
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);

        ticketList = deserializeResponseDto(response, Long.class);
        assertNotNull(ticketList);
        int numberOfTicketsB4 = ticketList.intValue();
        ticket = rulesDao.createTicket(ticket);

        response = getWebTarget()
                .path("/tickets/countopen/" + ticket.getUpdatedBy())
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);

        ticketList = deserializeResponseDto(response, Long.class);
        assertNotNull(ticketList);
        assertEquals(numberOfTicketsB4 + 1 , ticketList.intValue());

        rulesDao.removeTicketAfterTests(ticket);
        rulesDao.removeCustomRuleAfterTests(customRule);


    }

    @Test
    public void getNumberOfAssetsNotSendingTest() throws Exception{
        String response = getWebTarget()
                .path("/tickets/countAssetsNotSending")    //no tickets in the db
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);

        Long ticketList = deserializeResponseDto(response, Long.class);
        assertNotNull(ticketList);
        int numberOfTicketsB4 = ticketList.intValue();

        Ticket ticket = getCompleteTicket();
        ticket.setRuleGuid(ServiceConstants.ASSET_NOT_SENDING_RULE);
        rulesDao.createTicket(ticket);

        response = getWebTarget()
                .path("/tickets/countAssetsNotSending")    //no tickets in the db
                .request(MediaType.APPLICATION_JSON)
                .get(String.class);

        ticketList = deserializeResponseDto(response, Long.class);
        assertEquals(numberOfTicketsB4 +1 , ticketList.intValue());

        rulesDao.removeTicketAfterTests(ticket);
    }

    
    
    private static TicketQuery getBasicTicketQuery() {
        TicketQuery query = new TicketQuery();
        ListPagination pagination = new ListPagination();
        pagination.setPage(1);
        pagination.setListSize(100);
        query.setPagination(pagination);
        return query;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    private int getReturnCode(String responsDto) throws Exception{
        return objectMapper.readValue(responsDto, ObjectNode.class).get("code").asInt();
    }
    
    private static <T> T deserializeResponseDto(String responseDto, Class<T> clazz) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.readValue(responseDto, ObjectNode.class);
        JsonNode jsonNode = node.get("data");
        return objectMapper.readValue(objectMapper.writeValueAsString(jsonNode), clazz);
    }


    private Ticket getCompleteTicket() {
        Ticket ticket = new Ticket();
        ticket.setUpdated(new Date());
        ticket.setCreatedDate(new Date());
        ticket.setStatus(TicketStatusType.OPEN);
        ticket.setUpdatedBy("test user");
        ticket.setRuleGuid("tmp rule guid");
        ticket.setAssetGuid("tmp asset guid");
        ticket.setMovementGuid("tmp movement guid");
        ticket.setRuleName("tmp rule name");
        ticket.setTicketCount(1L);
        ticket.setChannelGuid("tmp channel guid");
        ticket.setMobileTerminalGuid(" tmp mobile terminal guid");
        ticket.setRecipient("tmp recipient");

        return ticket;
    }
    private CustomRuleType getCompleteNewCustomRule(){
        CustomRuleType customRule = new CustomRuleType();

        customRule.setName("Flag SWE && area DNK => Send to DNK" + " (" + System.currentTimeMillis() + ")");
        customRule.setAvailability(AvailabilityType.PRIVATE);
        customRule.setUpdatedBy("vms_admin_com");
        customRule.setActive(true);
        customRule.setArchived(false);

        // If flagstate = SWE
        CustomRuleSegmentType flagStateRule = new CustomRuleSegmentType();
        flagStateRule.setStartOperator("(");
        flagStateRule.setCriteria(CriteriaType.ASSET);
        flagStateRule.setSubCriteria(SubCriteriaType.FLAG_STATE);
        flagStateRule.setCondition(ConditionType.EQ);
        flagStateRule.setValue("SWE");
        flagStateRule.setEndOperator(")");
        flagStateRule.setLogicBoolOperator(LogicOperatorType.AND);
        flagStateRule.setOrder("0");
        customRule.getDefinitions().add(flagStateRule);

        // and area = DNK
        CustomRuleSegmentType areaRule = new CustomRuleSegmentType();
        areaRule.setStartOperator("(");
        areaRule.setCriteria(CriteriaType.AREA);
        areaRule.setSubCriteria(SubCriteriaType.AREA_CODE);
        areaRule.setCondition(ConditionType.EQ);
        areaRule.setValue("DNK");
        areaRule.setEndOperator(")");
        areaRule.setLogicBoolOperator(LogicOperatorType.NONE);
        areaRule.setOrder("1");
        customRule.getDefinitions().add(areaRule);

        // then send to FLUX DNK
        CustomRuleActionType action = new CustomRuleActionType();
        action.setAction(ActionType.SEND_TO_FLUX);
        action.setValue("FLUX DNK");
        action.setOrder("0");

        customRule.getActions().add(action);

        return customRule;
    }
}