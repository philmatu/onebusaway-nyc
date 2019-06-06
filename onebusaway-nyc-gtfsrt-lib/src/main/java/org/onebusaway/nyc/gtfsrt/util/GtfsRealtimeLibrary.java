/**
 * Copyright (c) 2016 Cambridge Systematics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.nyc.gtfsrt.util;

import com.google.transit.realtime.GtfsRealtime.*;
import org.joda.time.LocalDate;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.realtime.api.TimepointPredictionRecord;
import org.onebusaway.transit_data.model.VehicleStatusBean;
import org.onebusaway.transit_data.model.realtime.VehicleLocationRecordBean;
import org.onebusaway.transit_data.model.service_alerts.NaturalLanguageStringBean;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;
import org.onebusaway.transit_data.model.service_alerts.SituationAffectsBean;
import org.onebusaway.transit_data.model.service_alerts.SituationConsequenceBean;
import org.onebusaway.transit_data.model.service_alerts.TimeRangeBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.model.trips.TripStatusBean;

import java.util.List;

public class GtfsRealtimeLibrary {
    public static TripDescriptor.Builder makeTripDescriptor(TripBean tb, TripStatusBean status) {
        TripDescriptor.Builder trip = TripDescriptor.newBuilder();
        trip.setTripId(id(tb.getId()));
        trip.setRouteId(id(tb.getRoute().getId()));
        int direction = Integer.parseInt(tb.getDirectionId());
        trip.setDirectionId(direction);

        // start date - YYYYMMDD
        LocalDate ld = new LocalDate(status.getServiceDate());
        trip.setStartDate(ld.toString("yyyyMMdd"));

        return trip;
    }

    public static TripDescriptor.Builder makeTripDescriptor(TripBean tb, VehicleStatusBean vehicle) {
        return makeTripDescriptor(tb, vehicle.getTripStatus());
    }

    public static TripDescriptor.Builder makeTripDescriptor(VehicleStatusBean vehicle) {
        return makeTripDescriptor(vehicle.getTrip(), vehicle.getTripStatus());
    }

    public static Position.Builder makePosition(VehicleLocationRecordBean record) {
        Position.Builder pos = Position.newBuilder();
        pos.setLatitude((float) record.getCurrentLocation().getLat());
        pos.setLongitude((float) record.getCurrentLocation().getLon());
        pos.setBearing((float) record.getCurrentOrientation());
        return pos;
    }

    public static VehicleDescriptor.Builder makeVehicleDescriptor(VehicleStatusBean vsb) {
        VehicleDescriptor.Builder vehicle = VehicleDescriptor.newBuilder();
        vehicle.setId(vsb.getVehicleId());
        return vehicle;
    }

    public static VehicleDescriptor.Builder makeVehicleDescriptor(VehicleLocationRecordBean vlrb) {
        VehicleDescriptor.Builder vehicle = VehicleDescriptor.newBuilder();
        vehicle.setId(vlrb.getVehicleId());
        return vehicle;
    }

    public static TripUpdate.StopTimeUpdate.Builder makeStopTimeUpdate(TimepointPredictionRecord tpr) {
        TripUpdate.StopTimeUpdate.Builder builder = TripUpdate.StopTimeUpdate.newBuilder();
        builder.setStopId(tpr.getTimepointId().getId());
        builder.setArrival(makeStopTimeEvent(tpr.getTimepointPredictedTime()/1000));
        builder.setDeparture(makeStopTimeEvent(tpr.getTimepointPredictedTime()/1000)); // TODO: different?
        if (tpr.getStopSequence() >= 0)
            builder.setStopSequence(tpr.getStopSequence());
        return builder;
    }

    public static TripUpdate.StopTimeEvent.Builder makeStopTimeEvent(long time) {
        return TripUpdate.StopTimeEvent.newBuilder()
                .setTime(time);
    }

    public static Alert.Builder makeAlert(ServiceAlertBean alert) {

        Alert.Builder rtAlert = Alert.newBuilder();

        if (alert.getPublicationWindows() != null) {
            for (TimeRangeBean bean : alert.getPublicationWindows()) {
                rtAlert.addActivePeriod(range(bean));
            }
        }

        if (alert.getAllAffects() != null) {
            for (SituationAffectsBean affects : alert.getAllAffects()) {
                rtAlert.addInformedEntity(informedEntity(affects));
            }
        }

        if (alert.getConsequences() != null && !alert.getConsequences().isEmpty()) {
            SituationConsequenceBean cb = alert.getConsequences().get(0);
            // Effect and EEffect perfectly match string values
            rtAlert.setEffect(Alert.Effect.valueOf(cb.getEffect().toString()));
        }

        if (alert.getUrls() != null) {
            rtAlert.setUrl(translatedString(alert.getUrls()));
        }

        if (alert.getSummaries() != null) {
            rtAlert.setHeaderText(translatedString(alert.getSummaries()));
        }

        if (alert.getDescriptions() != null) {
            rtAlert.setDescriptionText(translatedString(alert.getDescriptions()));
        }

        return rtAlert;
    }

    private static TimeRange.Builder range(TimeRangeBean range) {
        TimeRange.Builder builder = TimeRange.newBuilder();
        if (range.getFrom() > 0)
            builder.setStart(range.getFrom()/1000);
        if (range.getTo() > 0)
            builder.setEnd(range.getTo()/1000);
        return builder;
    }

    private static EntitySelector.Builder informedEntity(SituationAffectsBean bean) {
        EntitySelector.Builder builder = EntitySelector.newBuilder();

        // If there is a trip ID or a direction ID, use a TripDescriptor (no duplicate route info)
        if (bean.getTripId() != null || bean.getDirectionId() != null) {
            TripDescriptor.Builder td = TripDescriptor.newBuilder();
            if (bean.getTripId() != null)
                td.setTripId(id(bean.getTripId()));
            if (bean.getRouteId() != null)
                td.setRouteId(id(bean.getRouteId()));
            if (bean.getDirectionId() != null)
                td.setDirectionId(Integer.parseInt(bean.getDirectionId()));
            builder.setTrip(td);
        } else if (bean.getRouteId() != null) {
            builder.setRouteId(id(bean.getRouteId()));
        }

        if (bean.getRouteId() != null) {
            builder.setAgencyId(AgencyAndId.convertFromString(bean.getRouteId()).getAgencyId());
        }
        if (bean.getAgencyId() != null)
            builder.setAgencyId(bean.getAgencyId());
        if (bean.getStopId() != null)
            builder.setStopId(id(bean.getStopId()));
        return builder;
    }

    private static TranslatedString.Builder translatedString(List<NaturalLanguageStringBean> beans) {
        TranslatedString.Builder string = TranslatedString.newBuilder();
        for (NaturalLanguageStringBean bean : beans) {
            TranslatedString.Translation.Builder tr = TranslatedString.Translation.newBuilder();
            tr.setLanguage(bean.getLang());
            tr.setText(bean.getValue());
            string.addTranslation(tr);
        }
        return string;
    }

    private static String id(String agencyAndId) {
        return AgencyAndId.convertFromString(agencyAndId).getId();
    }
}
