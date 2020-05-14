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
package eu.europa.ec.fisheries.uvms.movementrules.service.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import eu.europa.ec.fisheries.schema.movementrules.customrule.v1.AvailabilityType;

//@formatter:off
@Entity
@Table(name = "customrule")
@XmlRootElement
@NamedQueries({
        @NamedQuery(name = CustomRule.GET_RUNNABLE_CUSTOM_RULES, query = "SELECT r FROM CustomRule r WHERE r.active = true AND r.archived = false"), // for rule engine
        @NamedQuery(name = CustomRule.LIST_CUSTOM_RULES_BY_USER, query = "SELECT r FROM CustomRule r WHERE (r.availability = 'GLOBAL' OR r.availability = 'PUBLIC' OR r.updatedBy = :updatedBy)"),
        @NamedQuery(name = CustomRule.FIND_CUSTOM_RULE_GUID_FOR_TICKETS, query = "SELECT r.guid FROM CustomRule r LEFT OUTER JOIN r.ruleSubscriptionList s WHERE r.availability = 'GLOBAL' OR (s.owner = :owner AND s.type='TICKET')")
})
//@formatter:on
@DynamicUpdate
@DynamicInsert
public class CustomRule implements Serializable {
    
    public static final String GET_RUNNABLE_CUSTOM_RULES = "CustomRule.getValidCustomRule";
    public static final String LIST_CUSTOM_RULES_BY_USER = "CustomRule.listCustomRules";  // rule engine
    public static final String FIND_CUSTOM_RULE_GUID_FOR_TICKETS = "CustomRule.findRuleGuidsForTickets";

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "rule_id")
    private UUID guid;        //internal DB id

    @Column(name = "rule_name")
    private String name;    //exists in Type, same name

    @Column(name = "rule_description")
    private String description; //exists in Type, same name

    @Column(name = "rule_availability")
    private String availability;    //expects a value from AvailabilityType, exists in Type, same name

    @Column(name = "rule_organisation")
    private String organisation;    //exists in Type, same name

    @Column(name = "rule_startdate")
    private Instant startDate;         //exists in Type as the value timeIntervals

    @Column(name = "rule_enddate")
    private Instant endDate;           //exists in Type as the value timeIntervals

    @Column(name = "rule_active")
    private Boolean active;         //exists in Type, same name     TODO: Make requires not null

    @Column(name = "rule_archived")
    private Boolean archived;       //exists in Type, same name     TODO: Make requires not null

    @Column(name = "rule_aggregateinvocations")
    private boolean aggregateInvocations;

    @Column(name = "rule_updattim")
    @NotNull
    private Instant updated;           //exists in Type, same name

    @Column(name = "rule_upuser")
    @NotNull
    private String updatedBy;       //exists in Type, same name

    @OneToMany(mappedBy = "customRule", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RuleSubscription> ruleSubscriptionList;    //exists in Type as subscriptions

    @OneToMany(mappedBy = "customRule", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RuleSegment> ruleSegmentList;      //exists in Type as definitions

    @OneToMany(mappedBy = "customRule", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<RuleAction> ruleActionList;    //exists in Type as actions

    @OneToMany(mappedBy = "customRule", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Interval> intervals;           //exists in Type as timeIntervals

    @Column(name = "rule_lasttriggered")
    private Instant lastTriggered;


    public CustomRule copy(){
        CustomRule copy = new CustomRule();
        copy.setName(name);
        copy.setDescription(description);
        copy.setAvailability(availability);
        copy.setOrganisation(organisation);
        copy.setStartDate(startDate);
        copy.setEndDate(endDate);
        copy.setActive(active);
        copy.setArchived(archived);
        copy.setAggregateInvocations(aggregateInvocations);
        copy.setUpdated(updated);
        copy.setUpdatedBy(updatedBy);

        for (RuleSegment rs: ruleSegmentList) {
            copy.getRuleSegmentList().add(rs.copy(copy));
        }
        for (RuleAction ra: ruleActionList) {
            copy.getRuleActionList().add(ra.copy(copy));
        }

        for (Interval i : intervals) {
            copy.getIntervals().add(i.copy(copy));
        }

        return copy;
    }

    public UUID getGuid() {
        return guid;
    }

    public void setGuid(UUID guid) {
        this.guid = guid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public void setAvailability(AvailabilityType at) {availability = at.value();}

    public String getOrganisation() {
        return organisation;
    }

    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getArchived() {
        return archived;
    }

    public boolean isAggregateInvocations() {
        return aggregateInvocations;
    }

    public void setAggregateInvocations(boolean aggregateInvocations) {
        this.aggregateInvocations = aggregateInvocations;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    // @XmlTransient
    public List<RuleSegment> getRuleSegmentList() {
        if (ruleSegmentList == null) {
            ruleSegmentList = new ArrayList<>();
        }
        return ruleSegmentList;
    }

    public void setRuleSegmentList(List<RuleSegment> ruleSegmentList) {
        this.ruleSegmentList = ruleSegmentList;
    }

    // @XmlTransient
    public List<RuleAction> getRuleActionList() {
        if (ruleActionList == null) {
            ruleActionList = new ArrayList<>();
        }
        return ruleActionList;
    }

    public void setRuleActionList(List<RuleAction> ruleActionList) {
        this.ruleActionList = ruleActionList;
    }

    public List<Interval> getIntervals() {
        if (intervals == null) {
            intervals = new ArrayList<>();
        }
        return intervals;
    }

    public void setIntervals(List<Interval> intervals) {
        this.intervals = intervals;
    }

    public List<RuleSubscription> getRuleSubscriptionList() {
        if (ruleSubscriptionList == null) {
            ruleSubscriptionList = new ArrayList<>();
        }
        return ruleSubscriptionList;
    }

    public void setRuleSubscriptionList(List<RuleSubscription> ruleSubscriptionList) {
        this.ruleSubscriptionList = ruleSubscriptionList;
    }

    public Instant getLastTriggered() {
        return lastTriggered;
    }

    public void setLastTriggered(Instant lastTriggered) {
        this.lastTriggered = lastTriggered;
    }

    @Override
    public int hashCode() {
        return Objects.hash(guid, name, description, availability, organisation, startDate, endDate, active, archived, updated, updatedBy, ruleSubscriptionList, ruleSegmentList, ruleActionList, intervals);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CustomRule) {
            CustomRule other = (CustomRule) obj;
            if (!getRuleSegmentList().equals(other.getRuleSegmentList())) {
                return false;
            }
            if (!getRuleActionList().equals(other.getRuleActionList())) {
                return false;
            }
            if (!getIntervals().equals(other.getIntervals())) {
                return false;
            }
            return true;
        }
        return false;
    }
}