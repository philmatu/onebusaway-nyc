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
import junit.framework.TestCase;
import org.junit.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.gtfsrt.impl.VehicleUpdateFeedBuilderImpl;
import org.onebusaway.nyc.gtfsrt.service.VehicleUpdateFeedBuilder;
import org.onebusaway.nyc.gtfsrt.tds.MockTransitDataService;
import org.onebusaway.nyc.gtfsrt.util.InferredLocationReader;
import org.onebusaway.transit_data.model.TripStopTimeBean;
import org.onebusaway.transit_data.model.VehicleStatusBean;
import org.onebusaway.transit_data.model.realtime.VehicleLocationRecordBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.services.TransitDataService;
import org.onebusaway.transit_data_federation.impl.transit_graph.TransitGraphImpl;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

import java.util.ArrayList;
import java.util.List;

import static org.onebusaway.nyc.gtfsrt.tests.GtfsRtAssertLibrary.*;

/**
 * Test that OBA models match a VehiclePosition. Should be subclassed to test a particular instance.
 */
public abstract class VehiclePositionTest extends TestCase {

    private static final double TOLERANCE = 0.00001;

    private String _inferenceFile;

    private VehicleUpdateFeedBuilder _feedBuilder;

    private TransitDataService _transitDataService;


    public VehiclePositionTest(String gtfsFile, String defaultAgencyId, String blockTripMapFile, String inferenceFile) {

        _transitDataService = new MockTransitDataService(defaultAgencyId, gtfsFile, blockTripMapFile);
        _inferenceFile = inferenceFile;


        VehicleUpdateFeedBuilderImpl feedBuilder = new VehicleUpdateFeedBuilderImpl();
        _feedBuilder = feedBuilder;

    }

    @Test
    public void test() {
        List<VehicleLocationRecordBean> records = new InferredLocationReader().getRecords(_inferenceFile);
        assertFalse(records.isEmpty());

        for (VehicleLocationRecordBean record : records) {
            _transitDataService.submitVehicleLocation(record);
            VehicleStatusBean status = _transitDataService.getVehicleForAgency(record.getVehicleId(), record.getTimeOfRecord());
            VehiclePosition.Builder position = _feedBuilder.makeVehicleUpdate(status, record, null);
            assertVehiclePositionMatches(status, record, position);
            assertStopInfoMatches(status, position);
        }
    }


    private void assertVehiclePositionMatches(VehicleStatusBean status, VehicleLocationRecordBean record, VehiclePosition.Builder vehiclePosition) {
        assertTripDescriptorMatches(status.getTrip(), vehiclePosition.getTrip());
        assertPositionMatches(record, vehiclePosition.getPosition());
        assertVehicleDescriptorMatches(record, vehiclePosition.getVehicle());
        assertEquals(record.getTimeOfRecord(), vehiclePosition.getTimestamp()*1000);
    }

    private void assertPositionMatches(VehicleLocationRecordBean record, Position pos) {
        assertEquals(record.getCurrentLocation().getLat(), pos.getLatitude(), TOLERANCE);
        assertEquals(record.getCurrentLocation().getLon(), pos.getLongitude(), TOLERANCE);
        if (pos.hasBearing())
            assertEquals(record.getCurrentOrientation(), pos.getBearing(), TOLERANCE);
    }

    private void assertStopInfoMatches(VehicleStatusBean status, VehiclePosition.Builder vp) {
        // the relationship of the current stop (identified by stop_id and
        // current_stop_sequence) to the vehicle's position defaults to IN_TRANSIT_TO
        if (status.getTripStatus().getNextStop() == null)
            return;
        if (!vp.hasCurrentStatus() || vp.getCurrentStatus().equals(VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO)) {
            String stopId = AgencyAndId.convertFromString(status.getTripStatus().getNextStop().getId()).getId();
            assertEquals(stopId, vp.getStopId());
        }
        else
            throw new UnsupportedOperationException("Not implemented");

    }

    // for when we get gtfs stop sequence
    private void assertStopSequenceMatches(VehicleStatusBean status, VehiclePosition vp) {
        double dist = status.getTripStatus().getDistanceAlongTrip();
        if (dist == 0) // trip hasn't started
            assertEquals(0, vp.getCurrentStopSequence());

        TransitGraphImpl _graph = new TransitGraphImpl(); // to mock
        TripEntry trip = _graph.getTripEntryForId(AgencyAndId.convertFromString(status.getTrip().getId()));
        List<StopTimeEntry> stopTimeEntries = trip.getStopTimes();

        // todo
        List<TripStopTimeBean> tripStopTimes = new ArrayList<TripStopTimeBean>();

        if (stopTimeEntries.size() != tripStopTimes.size()) {
            throw new IllegalArgumentException("bad trip info");
        }

        for (int i = 0; i < tripStopTimes.size(); i++) {
            StopTimeEntry stopTimeEntry = stopTimeEntries.get(i);
            TripStopTimeBean stopTime = tripStopTimes.get(i);
            if (stopTime.getDistanceAlongTrip() >= dist) {
                assertEquals(stopTimeEntry.getSequence(), vp.getCurrentStopSequence());
                return;
            }
        }

        throw new IllegalArgumentException("Should not have reached here.");
    }

}
