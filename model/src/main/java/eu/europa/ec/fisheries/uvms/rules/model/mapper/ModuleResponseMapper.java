package eu.europa.ec.fisheries.uvms.rules.model.mapper;

import eu.europa.ec.fisheries.uvms.rules.model.exception.RulesModelMapperException;
import javax.jms.JMSException;
import javax.jms.TextMessage;

public class ModuleResponseMapper {

    private static void validateResponse(TextMessage response, String correlationId) throws RulesModelMapperException, JMSException {

        if (response == null) {
            throw new RulesModelMapperException("Error when validating response in ResponseMapper: Response is Null");
        }

        if (response.getJMSCorrelationID() == null) {
            throw new RulesModelMapperException("No corelationId in response (Null) . Expected was: " + correlationId);
        }

        if (!correlationId.equalsIgnoreCase(response.getJMSCorrelationID())) {
            throw new RulesModelMapperException("Wrong corelationId in response. Expected was: " + correlationId + "But actual was: " + response.getJMSCorrelationID());
        }

    }

}
