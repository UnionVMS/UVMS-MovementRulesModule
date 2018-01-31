/*
Developed by the European Commission - Directorate General for Maritime Affairs and Fisheries @ European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of
the License, or any later version. The IFDM Suite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
details. You should have received a copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.

*/
package eu.europa.fisheries.uvms.rules.service.business;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import eu.europa.ec.fisheries.schema.rules.template.v1.FactType;
import eu.europa.ec.fisheries.uvms.rules.service.business.TemplateFactory;
import org.drools.core.util.StringUtils;
import org.junit.Test;

/**
 * Created by kovian on 27/06/2017.
 */
public class TemplateFactoryTest {

    @Test
    public void testAllValuesAreInTemplate(){
        for(FactType factType : FactType.values()){
            String templateFileName = TemplateFactory.getTemplateFileName(factType);
            System.out.println("FactType : ["+factType+"]  Template : [[" + templateFileName + "]]");
            assertFalse(StringUtils.isEmpty(templateFileName));
        }
    }

    @Test
    public void testEmptyValWhenFactTypeDoesntExist(){
        String templateFileName = TemplateFactory.getTemplateFileName(null);
        assertTrue(StringUtils.isEmpty(templateFileName));
    }

}
