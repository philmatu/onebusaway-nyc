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
package org.onebusaway.nyc.vehicle_tracking.impl.inference.rules;

import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.geospatial.services.SphericalGeometryLibrary;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.BlockStateService;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.BlockStateService.BestBlockStates;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.MissingShapePointsException;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.Observation;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.ObservationCache;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.ObservationCache.EObservationCacheKey;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.ScheduleDeviationLibrary;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.BlockState;
import org.onebusaway.nyc.vehicle_tracking.impl.inference.state.VehicleState;
import org.onebusaway.nyc.vehicle_tracking.impl.particlefilter.SensorModelResult;
import org.onebusaway.realtime.api.EVehiclePhase;
import org.onebusaway.transit_data_federation.model.ProjectedPoint;

import com.google.common.collect.Iterables;

import org.apache.commons.math.util.FastMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import umontreal.iro.lecuyer.probdist.FoldedNormalDist;
import umontreal.iro.lecuyer.probdist.NormalDist;
import umontreal.iro.lecuyer.stat.Tally;

import java.util.HashSet;
import java.util.Set;

@Component
public class EdgeLikelihood implements SensorModelRule {

  private BlockStateService _blockStateService;
  private ScheduleDeviationLibrary _scheduleDeviationLibrary;
  private ObservationCache _observationCache;

  final private double distStdDev = 250.0;

  @Autowired
  public void setBlockStateService(BlockStateService blockStateService) {
    _blockStateService = blockStateService;
  }

  @Autowired
  public void setScheduleDeviationLibrary(
      ScheduleDeviationLibrary scheduleDeviationLibrary) {
    _scheduleDeviationLibrary = scheduleDeviationLibrary;
  }

  @Autowired
  public void setObservationCache(ObservationCache observationCache) {
    _observationCache = observationCache;
  }

  @Override
  public SensorModelResult likelihood(SensorModelSupportLibrary library,
      Context context) {

    final VehicleState state = context.getState();
    final Observation obs = context.getObservation();
    final EVehiclePhase phase = state.getJourneyState().getPhase();
    final SensorModelResult result = new SensorModelResult("pInProgress");

    if (!(phase == EVehiclePhase.IN_PROGRESS
        || phase == EVehiclePhase.DEADHEAD_BEFORE || phase == EVehiclePhase.DEADHEAD_DURING))
      return result;

    final VehicleState parentState = context.getParentState();
    if (obs.getPreviousObservation() == null || parentState == null)
      return phase == EVehiclePhase.IN_PROGRESS ? result.addResultAsAnd(
          "no previous observation/vehicle-state", 0.0)
          : result.addResultAsAnd("no previous observation/vehicle-state", 1.0);

    final BlockState blockState = state.getBlockState();
    /*
     * When we have no block-state, we use the best state with a route that
     * matches the observations DSC-implied route, if any.
     */
    if (blockState == null || phase != EVehiclePhase.IN_PROGRESS) {
      final Double bestInProgressProb = _observationCache.getValueForObservation(
          obs, EObservationCacheKey.ROUTE_LOCATION);

      double invResult;

      if (bestInProgressProb == null)
        invResult = 1.0;
      else
        invResult = 1.0 - bestInProgressProb;

      result.addResultAsAnd("pNotInProgress", invResult);
      return result;
    }

    final Set<BlockState> prevBlockStates = new HashSet<BlockState>();
    if (parentState.getBlockState() != null
        && parentState.getBlockState().getBlockInstance().equals(
            blockState.getBlockInstance()))
      prevBlockStates.add(parentState.getBlockState());

    if (prevBlockStates.isEmpty()
        || !EVehiclePhase.isActiveDuringBlock(parentState.getJourneyState().getPhase())) {
      try {

        final BestBlockStates prevBestStates = _blockStateService.getBestBlockLocations(
            obs.getPreviousObservation(), blockState.getBlockInstance(), 0,
            Double.POSITIVE_INFINITY);

        if (prevBestStates == null || prevBestStates.getAllStates().isEmpty())
          return result.addResultAsAnd("pNoPrevState", 0.0);

        prevBlockStates.addAll(prevBestStates.getAllStates());

      } catch (final MissingShapePointsException e) {
        e.printStackTrace();
        return result.addResultAsAnd("missing shapepoints", 0.0);
      }
    }

    /*
     * GPS Error
     */
    final CoordinatePoint p1 = blockState.getBlockLocation().getLocation();
    final ProjectedPoint p2 = obs.getPoint();

    final double d = SphericalGeometryLibrary.distance(p1.getLat(),
        p1.getLon(), p2.getLat(), p2.getLon());

    /*
     * Schedule Deviation
     */
    final int obsSchedDev = _scheduleDeviationLibrary.computeScheduleDeviation(
        blockState.getBlockInstance(), blockState.getBlockLocation(), obs);

    /*
     * Edge Movement
     */
    Double expectedDabDelta = null;
    Double actualDabDelta = null;

    final double pInP1 = 1.0 - FoldedNormalDist.cdf(10.0, 50.0, d);
    final double pInP2 = 1.0 - FoldedNormalDist.cdf(5.0, 40.0,
        obsSchedDev / 60.0);
    final double currentDab = blockState.getBlockLocation().getDistanceAlongBlock();

    final Tally avgRes = new Tally();

    /*
     * When there are multiple (potential) previously in-progress block-states,
     * we need to average over them to determine
     */
    for (final BlockState prevBlockState : prevBlockStates) {
      final double prevDab = prevBlockState.getBlockLocation().getDistanceAlongBlock();

      actualDabDelta = currentDab - prevDab;

      /*
       * Use whichever comes first: previous time incremented by observed change
       * in time, or last stop departure time.
       */
      final int expSchedTime = Math.min(
          (int) (prevBlockState.getBlockLocation().getScheduledTime() + (obs.getTime() - obs.getPreviousObservation().getTime()) / 1000),
          Iterables.getLast(
              prevBlockState.getBlockInstance().getBlock().getStopTimes()).getStopTime().getDepartureTime());

      // FIXME need to compensate for distances over the end of a trip...
      final BlockState expState = _blockStateService.getScheduledTimeAsState(
          blockState.getBlockInstance(), expSchedTime);

      expectedDabDelta = expState.getBlockLocation().getDistanceAlongBlock()
          - prevDab;

      final double pInP3Tmp = NormalDist.density(expectedDabDelta, distStdDev,
          actualDabDelta)
          / NormalDist.density(expectedDabDelta, distStdDev, expectedDabDelta);

      avgRes.add(pInP3Tmp);
    }

    final double pInP3 = avgRes.average();
    final double pLogInProgress = FastMath.log(pInP1) + FastMath.log(pInP2)
        + FastMath.log(pInP3);

    result.addResult("gps", pInP1);
    result.addResult("schedule", pInP2);
    result.addResult("distance", pInP3);

    final double pInProgress = FastMath.exp(pLogInProgress);

    /*
     * Keep the best (minimal location deviation) block state with a route
     * matching the dsc.
     */
    if (obs.getDscImpliedRouteCollections().contains(
        blockState.getBlockLocation().getActiveTrip().getTrip().getRouteCollection().getId())) {

      final Double bestInProgressProb = _observationCache.getValueForObservation(
          obs, EObservationCacheKey.ROUTE_LOCATION);

      if (bestInProgressProb == null || bestInProgressProb < pInProgress) {
        _observationCache.putValueForObservation(obs,
            EObservationCacheKey.ROUTE_LOCATION, pInProgress);
      }
    }

    result.addResultAsAnd("pInProgress", pInProgress);
    return result;
  }

}
