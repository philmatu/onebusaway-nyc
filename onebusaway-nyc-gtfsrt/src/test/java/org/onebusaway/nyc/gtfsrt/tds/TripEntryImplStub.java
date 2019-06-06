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
package org.onebusaway.nyc.gtfsrt.tds;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.LocalizedServiceId;
import org.onebusaway.transit_data_federation.services.transit_graph.BlockEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.FrequencyEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.RouteCollectionEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.RouteEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import java.util.List;

/**
 * Implementation of TripEntry backed by a GTFS Trip. (not fully implemented)
 */
public class TripEntryImplStub implements TripEntry {

    private Trip trip;
    private double tripDistance;

    public TripEntryImplStub(Trip trip) {
        this.trip = trip;
    }

    @Override
    public AgencyAndId getId() {
        return trip.getId();
    }

    @Override
    public RouteEntry getRoute() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RouteCollectionEntry getRouteCollection() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDirectionId() {
        return trip.getDirectionId();
    }

    @Override
    public BlockEntry getBlock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public LocalizedServiceId getServiceId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AgencyAndId getShapeId() {
        return trip.getShapeId();
    }

    @Override
    public List<StopTimeEntry> getStopTimes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public double getTotalTripDistance() {
        return tripDistance;
    }

    public void setTotalTripDistance(double tripDistance) {
        this.tripDistance = tripDistance;
    }

    @Override
    public FrequencyEntry getFrequencyLabel() {
        throw new UnsupportedOperationException();
    }

    public Trip getTrip() {
        return trip;
    }
}
