/**
 * Copyright (c) 2011 Metropolitan Transportation Authority
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
package org.onebusaway.nyc.vehicle_tracking.impl.inference;

import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.geospatial.services.SphericalGeometryLibrary;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.vehicle_tracking.model.NycRawLocationRecord;
import org.onebusaway.nyc.vehicle_tracking.model.library.TurboButton;
import org.onebusaway.transit_data_federation.impl.ProjectedPointFactory;
import org.onebusaway.transit_data_federation.model.ProjectedPoint;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

import java.util.Set;

public class Observation implements Comparable<Observation> {

  private final long _timestamp;

  private final NycRawLocationRecord _record;

  private final ProjectedPoint _point;

  private final String _lastValidDestinationSignCode;

  private final boolean atBase;

  private final boolean atTerminal;

  private final boolean outOfService;

  private Observation _previousObservation;

  private final Set<AgencyAndId> _dscImpliedRouteCollections;

  private final RunResults _runResults;

  private final boolean _hasValidDsc;

  private final Set<AgencyAndId> _impliedRouteCollections;

  private final String _assignedBlockId;

  private final boolean _isValidAssignedBlockId;

  public Observation(long timestamp, NycRawLocationRecord record,
      String lastValidDestinationSignCode, boolean atBase, boolean atTerminal,
      boolean outOfService, boolean hasValidDsc,
      Observation previousObservation, Set<AgencyAndId> dscImpliedRoutes,
      RunResults runResults, String assignedBlockId, boolean isValidAssignedBlockId) {
    _timestamp = timestamp;
    _record = record;
    _point = ProjectedPointFactory.forward(record.getLatitude(),
        record.getLongitude());
    _lastValidDestinationSignCode = lastValidDestinationSignCode;
    _dscImpliedRouteCollections = dscImpliedRoutes;
    _runResults = runResults;
    _impliedRouteCollections = Sets.newHashSet(Iterables.concat(
        dscImpliedRoutes, runResults.getRouteIds()));
    this.atBase = atBase;
    this.atTerminal = atTerminal;
    this.outOfService = outOfService;
    this._hasValidDsc = hasValidDsc;
    this._assignedBlockId = assignedBlockId;
    this._isValidAssignedBlockId = isValidAssignedBlockId;

    if (previousObservation == null) {
      this._timeDelta = null;
      this._distanceMoved = 0d;
      this._orientation = Double.NaN;
    } else {
      this._timeDelta = (timestamp - previousObservation.getTime()) / 1000d;
      this._distanceMoved = TurboButton.distance(
          previousObservation.getLocation(),
          _point.toCoordinatePoint());
      if (_distanceMoved == 0d) {
        this._orientation = previousObservation.getOrientation();
      } else {
        this._orientation = SphericalGeometryLibrary.getOrientation(
            previousObservation.getLocation().getLat(),
            previousObservation.getLocation().getLon(),
            record.getLatitude(),
            record.getLongitude()
            );
      }
    }

    _previousObservation = previousObservation;
  }

  public long getTime() {
    return _timestamp;
  }

  public NycRawLocationRecord getRecord() {
    return _record;
  }

  public ProjectedPoint getPoint() {
    return _point;
  }

  public String getLastValidDestinationSignCode() {
    return _lastValidDestinationSignCode;
  }

  public boolean isAtBase() {
    return atBase;
  }

  public boolean isAtTerminal() {
    return atTerminal;
  }

  public boolean hasOutOfServiceDsc() {
    return outOfService;
  }

  public CoordinatePoint getLocation() {
    return _point.toCoordinatePoint();
  }

  public Observation getPreviousObservation() {
    return _previousObservation;
  }

  public NycRawLocationRecord getPreviousRecord() {
    if (_previousObservation == null)
      return null;
    return _previousObservation.getRecord();
  }

  public void clearPreviousObservation() {
    _previousObservation = null;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper("Observation").add("atBase", atBase).add(
        "atTerminal", atTerminal).addValue(_record.toString()).toString();
  }

  public Set<AgencyAndId> getDscImpliedRouteCollections() {
    return _dscImpliedRouteCollections;
  }

  public Set<AgencyAndId> getImpliedRouteCollections() {
    return _impliedRouteCollections;
  }

  public Integer getFuzzyMatchDistance() {
    return _runResults.getBestFuzzyDist();
  }

  enum PointFunction implements Function<ProjectedPoint, Double> {
    getY {
      @Override
      public Double apply(final ProjectedPoint p) {
        return p.getY();
      }
    },
    getX {
      @Override
      public Double apply(final ProjectedPoint p) {
        return p.getX();
      }
    }
  }

  static private final Ordering<ProjectedPoint> _orderByXandY = Ordering.natural().nullsLast().onResultOf(
      PointFunction.getX).compound(
      Ordering.natural().nullsLast().onResultOf(PointFunction.getY));

  @Override
  public int compareTo(Observation o2) {

    if (this == o2)
      return 0;

    final int res = ComparisonChain.start().compare(_timestamp, o2._timestamp).compare(
        _point, o2._point, _orderByXandY).compare(
        _lastValidDestinationSignCode, o2._lastValidDestinationSignCode,
        Ordering.natural().nullsLast()).compare(_record, o2._record).compareFalseFirst(
        outOfService, o2.outOfService).compareFalseFirst(atTerminal, o2.atTerminal).compareFalseFirst(
        atBase, o2.atBase).compare(_runResults, o2._runResults)
          .compare(_assignedBlockId, o2._assignedBlockId)
          .compareFalseFirst(_isValidAssignedBlockId, o2._isValidAssignedBlockId)
          .result();

    return res;
  }

  private int _hash = 0;

  private final Double _timeDelta;

  private final double _orientation;

  private final double _distanceMoved;

  @Override
  public int hashCode() {

    if (_hash != 0)
      return _hash;

    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((_runResults == null) ? 0 : _runResults.hashCode());
    result = prime
        * result
        + ((_lastValidDestinationSignCode == null) ? 0
            : _lastValidDestinationSignCode.hashCode());
    result = prime * result + ((_point == null) ? 0 : _point.hashCode());
    result = prime * result + ((_record == null) ? 0 : _record.hashCode());
    result = prime * result + (int) (_timestamp ^ (_timestamp >>> 32));
    result = prime * result + (atBase ? 1231 : 1237);
    result = prime * result + (atTerminal ? 1231 : 1237);
    result = prime * result + (outOfService ? 1231 : 1237);
    result = prime * result + (_assignedBlockId == null ? 0 : _assignedBlockId.hashCode());
    result = prime * result + (_isValidAssignedBlockId ? 1231 : 1237);

    _hash = result;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof Observation))
      return false;
    final Observation other = (Observation) obj;
    if (_runResults == null) {
      if (other._runResults != null)
        return false;
    } else if (!_runResults.equals(other._runResults))
      return false;
    if (_lastValidDestinationSignCode == null) {
      if (other._lastValidDestinationSignCode != null)
        return false;
    } else if (!_lastValidDestinationSignCode.equals(other._lastValidDestinationSignCode))
      return false;
    if (_point == null) {
      if (other._point != null)
        return false;
    } else if (!_point.equals(other._point))
      return false;
    if (_record == null) {
      if (other._record != null)
        return false;
    } else if (!_record.equals(other._record))
      return false;
    if (_timestamp != other._timestamp)
      return false;
    if (atBase != other.atBase)
      return false;
    if (atTerminal != other.atTerminal)
      return false;
    if (outOfService != other.outOfService)
      return false;
    if (_assignedBlockId == null) {
      if(other._assignedBlockId != null)
        return false;
    } else if (!_assignedBlockId.equals(other._assignedBlockId)) {
      return false;
    }
    if (_isValidAssignedBlockId != other._isValidAssignedBlockId)
      return false;
      
    return true;
  }

  public String getOpAssignedRunId() {
    return _runResults.getAssignedRunId();
  }

  public Set<String> getBestFuzzyRunIds() {
    return _runResults.getFuzzyMatches();
  }

  public RunResults getRunResults() {
    return _runResults;
  }

  public boolean hasValidDsc() {
    return _hasValidDsc;
  }

  public Set<AgencyAndId> getRunImpliedRouteCollections() {
    return _runResults.getRouteIds();
  }

  /**
   * Time since last observation in seconds.
   * 
   * @return
   */
  public Double getTimeDelta() {
    return _timeDelta;
  }

  public double getDistanceMoved() {
    return _distanceMoved;
  }

  public double getOrientation() {
    return _orientation;
  }

  public String getAssignedBlockId() {
    return _assignedBlockId;
  }

  public boolean hasValidAssignedBlockId() {
    return _isValidAssignedBlockId;
  }

}
