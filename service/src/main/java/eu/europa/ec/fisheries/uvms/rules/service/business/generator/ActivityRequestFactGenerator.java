/*
 *
 * Developed by the European Commission - Directorate General for Maritime Affairs and Fisheries © European Union, 2015-2016.
 *
 * This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version. The IFDM Suite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package eu.europa.ec.fisheries.uvms.rules.service.business.generator;

import eu.europa.ec.fisheries.uvms.rules.service.business.AbstractFact;
import eu.europa.ec.fisheries.uvms.rules.service.business.fact.FishingActivityFact;
import eu.europa.ec.fisheries.uvms.rules.service.constants.FaReportDocumentType;
import eu.europa.ec.fisheries.uvms.rules.service.constants.FishingActivityType;
import eu.europa.ec.fisheries.uvms.rules.service.exception.RulesValidationException;
import eu.europa.ec.fisheries.uvms.rules.service.mapper.fact.ActivityFactMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import un.unece.uncefact.data.standard.fluxfareportmessage._3.FLUXFAReportMessage;
import un.unece.uncefact.data.standard.reusableaggregatebusinessinformationentity._20.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author padhyad
 * @author Gregory Rinaldi
 */
@Slf4j
public class ActivityRequestFactGenerator extends AbstractGenerator {

    private FLUXFAReportMessage fluxfaReportMessage;

    @Override
    public void setBusinessObjectMessage(Object businessObject) throws RulesValidationException {
        if (!(businessObject instanceof FLUXFAReportMessage)) {
            throw new RulesValidationException("Business object does not match required type");
        }
        this.fluxfaReportMessage = (FLUXFAReportMessage) businessObject;
    }

    @Override
    public List<AbstractFact> getAllFacts() {
        List<AbstractFact> facts = new ArrayList<>();
        facts.add(ActivityFactMapper.INSTANCE.generateFactForFluxReportMessage(fluxfaReportMessage));
        List<FAReportDocument> faReportDocuments = fluxfaReportMessage.getFAReportDocuments();
        if (CollectionUtils.isNotEmpty(faReportDocuments)) {
            facts.addAll(ActivityFactMapper.INSTANCE.generateFactForFaReportDocuments(faReportDocuments));
            for (FAReportDocument faReportDocument : faReportDocuments) {
                facts.addAll(addFacts(faReportDocument.getSpecifiedFishingActivities(), faReportDocument));
                facts.add(ActivityFactMapper.INSTANCE.generateFactForVesselTransportMean(faReportDocument.getSpecifiedVesselTransportMeans(), true));
            }
        }
        facts.removeAll(Collections.singleton(null));
        return facts;
    }

    private Collection<AbstractFact> addFacts(List<FishingActivity> specifiedFishingActivities, FAReportDocument faReportDocument) {
        List<AbstractFact> facts = new ArrayList<>();
        if (specifiedFishingActivities != null) {
            for (FishingActivity activity : specifiedFishingActivities) {
                facts.add(ActivityFactMapper.INSTANCE.generateFactForFishingActivity(activity));

                List<VesselTransportMeans> vesselTransportMeanses = activity.getRelatedVesselTransportMeans();
                facts.addAll(ActivityFactMapper.INSTANCE.generateFactForVesselTransportMeans(vesselTransportMeanses));
                addFactsForVesselTransportMeans(facts, vesselTransportMeanses);

                List<FACatch> faCatches = activity.getSpecifiedFACatches();
                facts.addAll(ActivityFactMapper.INSTANCE.generateFactsForFaCatchs(faCatches));
                addFactsForFaCatches(facts, faCatches);

                List<FishingGear> fishingGears = activity.getSpecifiedFishingGears();
                facts.addAll(ActivityFactMapper.INSTANCE.generateFactsForFishingGears(fishingGears));
                addFactsForFishingGearAndCharacteristics(facts, fishingGears);

                List<GearProblem> gearProblems = activity.getSpecifiedGearProblems();
                facts.addAll(ActivityFactMapper.INSTANCE.generateFactsForGearProblems(gearProblems));
                addFactsForGearProblems(facts, gearProblems);

                List<FLUXLocation> fluxLocations = activity.getRelatedFLUXLocations();
                facts.addAll(ActivityFactMapper.INSTANCE.generateFactsForFluxLocations(fluxLocations));
                addFactsForFLUXLocation(facts, fluxLocations);

                facts.add(ActivityFactMapper.INSTANCE.generateFactForFishingTrip(activity.getSpecifiedFishingTrip()));

                facts.add(addAdditionalValidationFact(activity, faReportDocument));
                facts.addAll(addAdditionalValidationfactForSubActivities(activity.getRelatedFishingActivities()));
            }
        }
        return facts;
    }

    private void addFactsForGearProblems(List<AbstractFact> facts, List<GearProblem> gearProblems) {
        for (GearProblem gearProblem : gearProblems) {
            List<FishingGear> relatedfishingGears = gearProblem.getRelatedFishingGears();
            addFactsForFishingGearAndCharacteristics(facts, relatedfishingGears);
            addFactsForFLUXLocation(facts, gearProblem.getSpecifiedFLUXLocations());
        }
    }

    private void addFactsForFaCatches(List<AbstractFact> facts, List<FACatch> faCatches) {
        for (FACatch faCatch : faCatches) {
            List<FishingGear> fishingGears = faCatch.getUsedFishingGears();
            addFactsForFishingGearAndCharacteristics(facts, fishingGears);
            addFactsForFLUXLocation(facts, faCatch.getSpecifiedFLUXLocations());
            addFactsForFLUXLocation(facts, faCatch.getDestinationFLUXLocations());
            facts.addAll(ActivityFactMapper.INSTANCE.generateFactForFishingTrips(faCatch.getRelatedFishingTrips()));
        }
    }

    private void addFactsForVesselTransportMeans(List<AbstractFact> facts, List<VesselTransportMeans> vesselTransportMeanses) {
        for (VesselTransportMeans vesselTransportMeans : vesselTransportMeanses) {
            if (CollectionUtils.isNotEmpty(vesselTransportMeans.getSpecifiedContactParties())) {
                for (ContactParty contactParty : vesselTransportMeans.getSpecifiedContactParties()) {
                    List<StructuredAddress> structuredAddresses = contactParty.getSpecifiedStructuredAddresses();
                    addFactsForStructuredAddress(facts, structuredAddresses);
                }
            }
        }
    }

    private void addFactsForFLUXLocation(List<AbstractFact> facts, List<FLUXLocation> fluxLocations) {
        for (FLUXLocation fluxLocation : fluxLocations) {
            List<StructuredAddress> structuredAddresses = fluxLocation.getPostalStructuredAddresses();
            addFactsForStructuredAddress(facts, structuredAddresses);
            facts.add(ActivityFactMapper.INSTANCE.generateFactsForStructureAddress(fluxLocation.getPhysicalStructuredAddress()));
        }
    }

    private void addFactsForStructuredAddress(List<AbstractFact> facts, List<StructuredAddress> structuredAddresses) {
        if (CollectionUtils.isNotEmpty(structuredAddresses)) {
            facts.addAll(ActivityFactMapper.INSTANCE.generateFactsForStructureAddresses(structuredAddresses));
        }
    }

    private void addFactsForFishingGearAndCharacteristics(List<AbstractFact> facts, List<FishingGear> fishingGears) {
        if (CollectionUtils.isNotEmpty(fishingGears)) {
            facts.addAll(ActivityFactMapper.INSTANCE.generateFactsForFishingGears(fishingGears));
            for (FishingGear fishingGear : fishingGears) {
                List<GearCharacteristic> gearCharacteristics = fishingGear.getApplicableGearCharacteristics();
                if (CollectionUtils.isNotEmpty(gearCharacteristics)) {
                    facts.addAll(ActivityFactMapper.INSTANCE.generateFactsForGearCharacteristics(gearCharacteristics));
                }
            }
        }
    }

    private AbstractFact addAdditionalValidationFact(FishingActivity activity, FAReportDocument faReportDocument) {
        AbstractFact abstractFact = null;
        try {
            if (activity != null && activity.getTypeCode() != null) {
                FishingActivityType fishingActivityType = FishingActivityType.valueOf(activity.getTypeCode().getValue());
                switch (fishingActivityType) {
                    case DEPARTURE:
                        abstractFact = ActivityFactMapper.INSTANCE.generateFactsForFaDeparture(activity, faReportDocument);
                        break;
                    case ARRIVAL:
                        if (FaReportDocumentType.DECLARATION.equals(faReportDocument.getTypeCode().getValue())) {
                            abstractFact = ActivityFactMapper.INSTANCE.generateFactsForDeclarationOfArrival(activity, faReportDocument);
                        } else if (FaReportDocumentType.NOTIFICATION.equals(faReportDocument.getTypeCode().getValue())) {
                            abstractFact = ActivityFactMapper.INSTANCE.generateFactsForPriorNotificationOfArrival(activity, faReportDocument);
                        }
                        break;
                    case AREA_ENTRY:
                        abstractFact = ActivityFactMapper.INSTANCE.generateFactsForEntryIntoSea(activity, faReportDocument);
                        break;
                    case AREA_EXIT:
                        abstractFact = ActivityFactMapper.INSTANCE.generateFactsForExitArea(activity, faReportDocument);
                        break;
                    case JOINT_FISHING_OPERATION:
                        abstractFact = ActivityFactMapper.INSTANCE.generateFactsForJointFishingOperation(activity, faReportDocument);
                        break;
                    case LANDING:
                        abstractFact = ActivityFactMapper.INSTANCE.generateFactsForLanding(activity, faReportDocument);
                        break;
                    case TRANSHIPMENT:
                        if (FaReportDocumentType.DECLARATION.equals(faReportDocument.getTypeCode().getValue())) {
                            abstractFact = ActivityFactMapper.INSTANCE.generateFactsForTranshipment(activity, faReportDocument);
                        } else if (FaReportDocumentType.NOTIFICATION.equals(faReportDocument.getTypeCode().getValue())) {
                            abstractFact = ActivityFactMapper.INSTANCE.generateFactsForNotificationOfTranshipment(activity, faReportDocument);
                        }
                        break;
                    default:
                        log.info("No rule to be applied for the received activity type : "+fishingActivityType);

                }
            }
        } catch (IllegalArgumentException e) {
            log.error("No such Fishing activity type", e);
        }

        return abstractFact;
    }

    private Collection<AbstractFact> addAdditionalValidationfactForSubActivities(List<FishingActivity> fishingActivities) {
        List<AbstractFact> facts = new ArrayList<>();
        if (fishingActivities != null) {
            for (FishingActivity activity : fishingActivities) {
                FishingActivityFact fishingActivityFact = ActivityFactMapper.INSTANCE.generateFactForFishingActivity(activity, true);
                fishingActivityFact.setIsSubActivity(true);
                facts.add(fishingActivityFact);
            }
        }
        return facts;
    }
}