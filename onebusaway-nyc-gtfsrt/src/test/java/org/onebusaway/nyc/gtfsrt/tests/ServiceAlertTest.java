/**
 * Copyright (C) 2017 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.nyc.gtfsrt.tests;

import com.google.transit.realtime.GtfsRealtime.*;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.gtfsrt.impl.ServiceAlertFeedBuilderImpl;
import org.onebusaway.nyc.gtfsrt.service.ServiceAlertFeedBuilder;
import org.onebusaway.nyc.gtfsrt.util.ServiceAlertReader;
import org.onebusaway.transit_data.model.service_alerts.EEffect;
import org.onebusaway.transit_data.model.service_alerts.NaturalLanguageStringBean;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;
import org.onebusaway.transit_data.model.service_alerts.SituationAffectsBean;
import org.onebusaway.transit_data.model.service_alerts.SituationConsequenceBean;
import org.onebusaway.transit_data.model.service_alerts.TimeRangeBean;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * abstract to surefire will not try to execute.  Intended to be
 * subclassed by test cases.
 */
public abstract class ServiceAlertTest {
    private String inferenceFile;

    private ServiceAlertFeedBuilder feedBuilder = new ServiceAlertFeedBuilderImpl();

    public ServiceAlertTest(String inferenceFile) {
        this.inferenceFile = inferenceFile;
    }

    public void test() {
        List<ServiceAlertBean> records = new ServiceAlertReader().getRecords(inferenceFile);
        assertFalse(records.isEmpty());

        for (ServiceAlertBean bean : records) {
            Alert.Builder alert = feedBuilder.getAlertFromServiceAlert(bean);
            assertServiceAlertMatches(bean, alert);
        }
    }

    private static void assertServiceAlertMatches(ServiceAlertBean bean, Alert.Builder feed) {
        assertActivePeriodMatches(bean.getPublicationWindows(), feed.getActivePeriodList());
        assertInformedEntityMatches(bean.getAllAffects(), feed.getInformedEntityList());

        if (feed.hasCause())
            assertCauseMatches(bean.getReason(), feed.getCause());
        else
            assertNull(bean.getReason());

        if (feed.hasEffect())
            assertEffectMatches(bean.getConsequences(), feed.getEffect());
        else
            assertNull(bean.getConsequences());

        if (feed.hasUrl())
            assertLanguageStringsMatch(bean.getUrls(), feed.getUrl());
        else
            assertNull(bean.getUrls());

        if (feed.hasHeaderText())
            assertLanguageStringsMatch(bean.getSummaries(), feed.getHeaderText());
        else
            assertNull(bean.getSummaries());

        if (feed.hasDescriptionText())
            assertLanguageStringsMatch(bean.getDescriptions(), feed.getDescriptionText());
        else
            assertNull(bean.getDescriptions());
    }

    private static void assertActivePeriodMatches(List<TimeRangeBean> windows, List<TimeRange> periods) {
        if (windows == null || windows.isEmpty()) {
            assertTrue(periods == null || periods.isEmpty());
        }
        assertEquals(windows.size(), periods.size());

        for (TimeRangeBean window : windows) {
            boolean foundMatch = false;
            for (TimeRange period : periods) {
                foundMatch |= windowPeriodMatch(window, period);
            }
            assertTrue(foundMatch);
        }
    }

    private static boolean windowPeriodMatch(TimeRangeBean window, TimeRange period) {
        if ((window.getFrom() > 0) != period.hasStart())
            return false;
        else if (period.hasStart() && period.getStart() != window.getFrom()/1000)
            return false;

        if ((window.getTo() > 0) != period.hasEnd())
            return false;
        else if (period.hasEnd() && period.getEnd() != window.getTo()/1000)
            return false;

        return true;
    }

    private static void assertInformedEntityMatches(List<SituationAffectsBean> affects, List<EntitySelector> entities) {
        if (affects == null || affects.isEmpty()) {
            assertTrue(entities == null || entities.isEmpty());
        }
        assertEquals(affects.size(), entities.size());
        for (SituationAffectsBean bean : affects) {
            boolean foundMatch = false;
            for (EntitySelector entity : entities) {
                foundMatch |= affectsEntityMatch(bean, entity);
            }
           assertTrue(foundMatch);
        }
    }

    private static boolean affectsEntityMatch(SituationAffectsBean affects, EntitySelector entity) {
        String beanAgencyId = affects.getAgencyId();
        if (beanAgencyId == null && affects.getRouteId() != null) {
            beanAgencyId = AgencyAndId.convertFromString(affects.getRouteId()).getAgencyId();
        }
        if ((beanAgencyId != null) != entity.hasAgencyId())
            return false;
        if (beanAgencyId != null && !beanAgencyId.equals(entity.getAgencyId()))
            return false;
        if ((affects.getRouteId() != null) != (entity.hasRouteId() || entity.getTrip().hasRouteId()))
            return false;
        if (affects.getRouteId() != null && entity.hasRouteId() && !id(affects.getRouteId()).equals(entity.getRouteId()))
            return false;
        if ((affects.getTripId() != null || affects.getDirectionId() != null) != entity.hasTrip())
            return false;
        if ((affects.getStopId() != null) != entity.hasStopId())
            return false;
        if (entity.hasStopId() && !id(affects.getStopId()).equals(entity.getStopId()))
            return false;
        if (entity.hasTrip()) {
            TripDescriptor td = entity.getTrip();
            if (td.hasTripId() != (affects.getTripId() != null))
                return false;
            if (td.hasTripId() && (!td.getTripId().equals(id(affects.getTripId()))))
                return false;
            if (td.hasRouteId() != (affects.getRouteId() != null))
                return false;
            if (td.hasRouteId() && !td.getRouteId().equals(id(affects.getRouteId())))
                return false;
            if (td.hasDirectionId() != (affects.getDirectionId() != null))
                return false;
            if (td.hasDirectionId() && (!Integer.toString(td.getDirectionId()).equals(affects.getDirectionId())))
                return false;
        }
        return true;
    }

    private static void assertCauseMatches(String reason, Alert.Cause cause) {
        assertEquals(cause.toString(), reason);
    }

    private static void assertEffectMatches(List<SituationConsequenceBean> consequences, Alert.Effect effect) {
        boolean foundMatch = false;
        for (SituationConsequenceBean bean : consequences) {
            EEffect beanEffect = bean.getEffect();
            foundMatch |= beanEffect.toString().equals(effect.toString());
        }
       assertTrue(foundMatch);
    }

    private static void assertLanguageStringsMatch(List<NaturalLanguageStringBean> beans, TranslatedString translatedString) {
        List<TranslatedString.Translation> translations = translatedString.getTranslationList();
        assertEquals(translations.size(), beans.size());
        for (NaturalLanguageStringBean bean : beans) {
            boolean foundMatch = false;
            for (TranslatedString.Translation ts : translations) {
                foundMatch |= bean.getLang().equals(ts.getLanguage()) && bean.getValue().equals(ts.getText());
            }
            assertTrue(foundMatch);
        }
    }

    private static String id(String id) {
        return AgencyAndId.convertFromString(id).getId();
    }
}
