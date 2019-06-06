package org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.RawRunData;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.StifTrip;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.StifTripType;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.model.ServiceCode;
import org.onebusaway.nyc.transit_data_federation.model.nyc.SupplimentalTripInformation;
import org.opentripplanner.common.model.P2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StifAggregatorImpl {

  private static final int MAX_BLOCK_ID_LENGTH = 64;

  private Logger _log = LoggerFactory.getLogger(StifAggregatorImpl.class);

  private AbnormalStifDataLoggerImpl _AbnormalStifDataLogger;
  public void setAbnormalStifDataLoggerImpl(AbnormalStifDataLoggerImpl a){
    _AbnormalStifDataLogger = a;
  }

  private StifLoaderImpl _stifLoader;
  public void setStifLoader(StifLoaderImpl sl){
    _stifLoader = sl;
  }

  private HashMap<String, Set<AgencyAndId>> routeIdsByDsc = new HashMap<String, Set<AgencyAndId>>(20000);
  private ArrayList<StifTrip> pullouts = new ArrayList<StifTrip>(20000);
  private HashMap<String, List<StifTrip>> tripsByRun = new HashMap<String, List<StifTrip>>(20000);
  private HashSet<StifTrip> unmatchedTrips = new HashSet<StifTrip>(1024);

  private HashSet<Trip> usedGtfsTrips = new HashSet<Trip>(1000000);

  private HashSet<Route> routesWithTrips = new HashSet<Route>(500);
  
  private HashMap<AgencyAndId, SupplimentalTripInformation> tripInfo = new HashMap<AgencyAndId, SupplimentalTripInformation>(1000000);

  public HashMap<String, Set<AgencyAndId>> getRouteIdsByDsc(){
    return routeIdsByDsc;
  }
  
  public HashSet<StifTrip> getUnmatchedTrips() {
	return unmatchedTrips;
  }

  public void computeBlocksFromRuns() {
    int blockNo = 0;

    Map<ServiceCode, List<StifTrip>> rawData = _stifLoader.getRawStifData();
    //    _log.debug("number of service codes " +rawData.size() + ":" + rawData.keySet().toString());

    for (Map.Entry<ServiceCode, List<StifTrip>> entry : rawData.entrySet()) {
      List<StifTrip> rawTrips = entry.getValue();

      populateTripDetails(rawTrips);

      // for each pull-out, start a new block
//      _log.debug(pullouts.size() + " pullouts on " + entry.getKey().toString());
      for (StifTrip pullout : pullouts) {
//              _log.debug("pullout " + pullout.toString() + " unmatched trips size" + unmatchedTrips.size());

        blockNo ++;
        StifTrip lastTrip = pullout;
        int i = 0;
        HashSet<P2<String>> blockIds = new HashSet<P2<String>>();

        while (lastTrip.type != StifTripType.PULLIN) {
          unmatchedTrips.remove(lastTrip);
          if (++i > 200) {
            infiniteLoopMessage(lastTrip);
            break;
          }

          String nextRunId = getNextRunID(lastTrip);

          if (nextRunId == null){
            _log.debug("no next run id for " + lastTrip.toString());
            break;
          }
          List<StifTrip> trips = tripsByRun.get(nextRunId);

          if (trips == null) {
            _log.warn("No trips for run " + nextRunId);
            break;
          }

          int nextTripStartTime = lastTrip.listedLastStopTime + lastTrip.recoveryTime * 60;
          @SuppressWarnings("unchecked")
          int index = Collections.binarySearch(trips, nextTripStartTime, new RawTripComparator());
          
          //binarySearch on a list returns a negative number when the search key is not found.
          if (index < 0) {
            index = -(index + 1);
            _log.debug("index reset to " + index);
          }

          if(!tripExists(index, nextRunId, lastTrip, trips.size())){
            _log.debug(index + " next run " + nextRunId + " trip " + lastTrip.toString() + " size " + trips.size());
            break;
          }

          StifTrip trip = trips.get(index);

          if(!isTripDifferent(lastTrip, trip, index, nextTripStartTime, nextRunId, trips)){
            _log.debug(lastTrip.toString() + " and " + trip.toString() +" not different");
            break;
          }
          
          lastTrip = trip;
          matchGTFSToSTIF(lastTrip, trip, pullout, blockIds);
        }
        unmatchedTrips.remove(lastTrip);
        dumpBlocksOut(blockIds, lastTrip, pullout);
      }
      logUnmatchedTrip(blockNo, entry);
    }
    determineIfRoutesAreInStif();
  }

  private void populateTripDetails(List<StifTrip> rawTrips){
	  tripsByRun.clear();
	  pullouts.clear();
    for (StifTrip trip : rawTrips) {
      String runId = trip.getRunIdWithDepot();
      List<StifTrip> tripsForThisRun = tripsByRun.get(runId);

      if (tripsForThisRun == null) {
        tripsForThisRun = new ArrayList<StifTrip>();
        tripsByRun.put(runId, tripsForThisRun);
      }

      unmatchedTrips.add(trip);
      tripsForThisRun.add(trip);
      if (trip.type == StifTripType.PULLOUT) {
        pullouts.add(trip);
      }
      else if (trip.type == StifTripType.DEADHEAD && 
    		  trip.listedFirstStopTime == trip.listedLastStopTime + trip.recoveryTime) {
		        _log.warn("Zero-length deadhead.  If this immediately follows a pullout, "
		            + "tracing might fail.  If it does, we will mark some trips as trips "
		            + "without pullout.");
      }
    }
    
    for (List<StifTrip> byRun : tripsByRun.values()) {
      Collections.sort(byRun);
    }

  }

  public void infiniteLoopMessage(StifTrip lastTrip){
    _log.warn("\n We seem to be caught in an infinite loop; this is usually caused\n"
        + "by two trips on the same run having the same start time.  Since nobody\n"
        + "can be in two places at once, this is an error in the STIF.  Some trips\n"
        + "will end up with missing blocks and the log will be screwed up.  A \n"
        + "representative trip starts at "
        + lastTrip.firstStop + " of type " + lastTrip.type
        + "\n at " + lastTrip.firstStopTime + " on " + lastTrip.getRunIdWithDepot() + " on " + lastTrip.serviceCode);
  }

  public String getNextRunID(StifTrip lastTrip){
    // the depot may differ from the pullout depot
    String nextRunId = lastTrip.getNextRunIdWithDepot();
    if (nextRunId == null) {
      _AbnormalStifDataLogger.log("non_pullin_without_next_movement.csv", lastTrip.id, lastTrip.path, lastTrip.lineNumber); 

      _log.warn("A non-pullin has no next run; some trips will end up with missing blocks"
          + " and the log will be messed up. The bad trip starts at " + lastTrip.firstStop + " at "
          + lastTrip.firstStopTime + " on " + lastTrip.getRunIdWithDepot() + " on " + lastTrip.serviceCode);
    }
    return nextRunId;
  }

  private boolean tripExists(int index, String nextRunId, StifTrip lastTrip, int trips_size){

    if (index >= trips_size) {
      _log.warn("The preceding trip says that the run "
          + nextRunId
          + " is next, but there are no trips after "
          + lastTrip.firstStopTime
          + ", so some trips will end up with missing blocks."
          + " The last trip starts at " + lastTrip.firstStop + " at "
          + lastTrip.firstStopTime + " on " + lastTrip.getRunIdWithDepot() + " on " + lastTrip.serviceCode);
      return false;
    }
    return true;
  }

  public boolean isTripDifferent(StifTrip lastTrip, StifTrip trip, int index, int nextTripStartTime, String nextRunId, List<StifTrip> trips){
    if (trip == lastTrip) {
      //we have two trips with the same start time -- usually one is a pull-out of zero-length
      //we don't know if we got the first one or the last one, since Collections.binarySearch
      //makes no guarantees
      if (index > 0 && trips.get(index-1).listedFirstStopTime == nextTripStartTime){
        index--;
        trip = trips.get(index);
      }

      else if (index < trips.size() - 1 && trips.get(index+1).listedFirstStopTime == nextTripStartTime){
        index++;
      } else {
        _log.warn("The preceding trip says that the run "
            + nextRunId
            + " is next, and that the next trip should start at " + nextTripStartTime
            + ". As it happens, *this* trip starts at that time, but no other trips on"
            + " this run do, so some trips will end up with missing blocks."
            + " The last trip starts at " + lastTrip.firstStop + " at "
            + lastTrip.firstStopTime + " on " + lastTrip.getRunIdWithDepot() + " on " + lastTrip.serviceCode);
        return false;
      }
    }
    return true;
  }

  public void matchGTFSToSTIF(StifTrip lastTrip, StifTrip trip, StifTrip pullout, HashSet<P2<String>> blockIds){
    for (Trip gtfsTrip : lastTrip.getGtfsTrips()) {
      RawRunData rawRunData = _stifLoader.getRawRunDataByTrip().get(gtfsTrip);


      String blockId;
      if (trip.agencyId.equals("MTA NYCT")) {
        blockId = gtfsTrip.getServiceId().getId() + "_" +
            trip.serviceCode.getLetterCode() + "_" +
            rawRunData.getDepotCode() + "_" +
            pullout.firstStopTime + "_" +
            pullout.runId;
      } else {
        blockId = gtfsTrip.getServiceId().getId() + "_" + trip.blockId;
      }

      blockId = blockId.intern();
      blockIds.add(new P2<String>(blockId, gtfsTrip.getServiceId().getId()));
      gtfsTrip.setBlockId(blockId);
      _stifLoader.getGtfsMutableRelationalDao().updateEntity(gtfsTrip);

      AgencyAndId routeId = gtfsTrip.getRoute().getId();
      addToMapSet(routeIdsByDsc, trip.getDsc(), routeId);
      _AbnormalStifDataLogger.dumpBlockDataForTrip(trip, gtfsTrip.getServiceId().getId(),
          gtfsTrip.getId().getId(), blockId, routeId.getId());

      usedGtfsTrips.add(gtfsTrip);
      addToSupplimentalTripInfo(gtfsTrip, trip);
    }
    if (lastTrip.type == StifTripType.DEADHEAD) {
      for (P2<String> blockId : blockIds) {
        String tripId = String.format("deadhead_%s_%s_%s_%s_%s", blockId.getSecond(), lastTrip.firstStop, lastTrip.firstStopTime, lastTrip.lastStop, lastTrip.runId);
        _AbnormalStifDataLogger.dumpBlockDataForTrip(lastTrip, blockId.getSecond(), tripId, blockId.getFirst(), "no gtfs trip");
      }
    }
  }

  // public for testing :/
  public void addToSupplimentalTripInfo(Trip gtfsTrip, StifTrip trip){
	  SupplimentalTripInformation _sti = new SupplimentalTripInformation(trip.type, trip.busType, trip.direction);
	  tripInfo.put(gtfsTrip.getId(), _sti);
  }
  
  private void dumpBlocksOut(HashSet<P2<String>> blockIds, StifTrip lastTrip, StifTrip pullout){
    for (P2<String> blockId : blockIds) {
      String pulloutTripId = String.format("pullout_%s_%s_%s_%s", blockId.getSecond(), lastTrip.firstStop, lastTrip.firstStopTime, lastTrip.runId);
      _AbnormalStifDataLogger.dumpBlockDataForTrip(pullout, blockId.getSecond(), pulloutTripId , blockId.getFirst(), "no gtfs trip");
      String pullinTripId = String.format("pullin_%s_%s_%s_%s", blockId.getSecond(), lastTrip.lastStop, lastTrip.lastStopTime, lastTrip.runId);
      _AbnormalStifDataLogger.dumpBlockDataForTrip(lastTrip, blockId.getSecond(), pullinTripId, blockId.getFirst(), "no gtfs trip");
    }
  }

  private void logUnmatchedTrip(int blockNo, Entry<ServiceCode, List<StifTrip>> entry){
    for (StifTrip trip : unmatchedTrips) {
      _log.warn("STIF trip: " + trip + " on schedule " + entry.getKey()
      + " trip type " + trip.type
      + " must not have an associated pullout");
      for (Trip gtfsTrip : trip.getGtfsTrips()) {
        blockNo++;
        String blockId = gtfsTrip.getServiceId().getId() + "_"
            + trip.serviceCode.getLetterCode() + "_" + trip.firstStop + "_"
            + trip.firstStopTime + "_" + trip.runId.replace("-", "_")
            + blockNo + "_orphn";
        if (blockId.length() > MAX_BLOCK_ID_LENGTH) {
          blockId = truncateId(blockId);
        }
        _log.warn("Generating single-trip block id for GTFS trip: "
            + gtfsTrip.getId() + " : " + blockId);
        gtfsTrip.setBlockId(blockId);
        _AbnormalStifDataLogger.dumpBlockDataForTrip(trip, gtfsTrip.getServiceId().getId(),
            gtfsTrip.getId().getId(), blockId, gtfsTrip.getRoute().getId().getId());
        _AbnormalStifDataLogger.log("stif_trips_without_pullout.csv", trip.id, trip.path,
            trip.lineNumber, gtfsTrip.getId(), blockId);
        usedGtfsTrips.add(gtfsTrip);
      }
    }
  }

  //static for unit tests
  public static String truncateId(String id) {
    if (id == null) return null;
    return id.replaceAll("[aeiouy\\s]", "");
  }

  private <T, U> void addToMapSet(Map<T, Set<U>> mapList, T key, U value) {
    Set<U> list = mapList.get(key);
    if (list == null) {
      list = new HashSet<U>();
      mapList.put(key, list);
    }
    list.add(value);
  }

  private void determineIfRoutesAreInStif() {

    _AbnormalStifDataLogger.header("gtfs_trips_with_no_stif_match.csv", "gtfs_trip_id,stif_trip");
    Collection<Trip> allTrips = _stifLoader.getGtfsMutableRelationalDao().getAllTrips();

    for (Trip trip : allTrips) {
      if (usedGtfsTrips.contains(trip)) {
        if (!routesWithTrips.contains(trip.getRoute())) {
              routesWithTrips.add(trip.getRoute());
          }
      } else {
        _AbnormalStifDataLogger.log("gtfs_trips_with_no_stif_match.csv", trip.getId(), _stifLoader.getSupport().getTripAsIdentifier(trip));
      }
    }

    _AbnormalStifDataLogger.header("route_ids_with_no_trips.csv", "agency_id,route_id");
    for (Route route : _stifLoader.getGtfsMutableRelationalDao().getAllRoutes()) {
      if (routesWithTrips.contains(route)) {
        continue;
      }
      _AbnormalStifDataLogger.log("route_ids_with_no_trips.csv", route.getId().getAgencyId(), route.getId().getId());
    }
  }

  public HashMap<String, Set<AgencyAndId>> getRoutesByDSC(){
    return routeIdsByDsc;
  }

  @SuppressWarnings("rawtypes")
  private class RawTripComparator implements Comparator {
    @Override
    public int compare(Object o1, Object o2) {
//      _log.debug("comparing " + o1.toString() + " and " + o2.toString());
      if (o1 instanceof StifTrip){
        StifTrip trip1 = ((StifTrip) o1);
        
        if (o2 instanceof Integer) {
          if (trip1.listedFirstStopTime == (Integer) o2
              && trip1.type == StifTripType.PULLOUT){
            // o1 is trip (and a pullout), o2 is an int. Both have the same start time.
            // RETURN PULLOUT FIRST
            return -1;
          }else {
            // o1 is a trip, o2 is an int
            return trip1.listedFirstStopTime - ((Integer) o2);
          }
          
        } else {
          // o1 and o2 are trips
          StifTrip trip2 = (StifTrip) o2;
          return (trip1.listedFirstStopTime - trip2.listedFirstStopTime);
        }} 
      //  both o1 and o2 are ints
      else if (o1 instanceof Integer) {
        if (o2 instanceof Integer) {
          return ((Integer) o1) - ((Integer) o2);
        } else {         
          // o11 is an int, o2 is a StifTrip  
          StifTrip trip2 = (StifTrip) o2;
          return ((Integer) o1) - trip2.listedFirstStopTime;
        }
      } else {
        return 0;
      }
    }
  }
  // unit test methods
  public int getMatchedGtfsTripsCount(){
    return usedGtfsTrips.size();
  }

  public int getRoutesWithTripsCount() {
    return routesWithTrips.size();
  }

  public int getUnmatchedTripsSize() {
    return unmatchedTrips.size();
  }

  public HashMap<AgencyAndId, SupplimentalTripInformation> getSupplimentalTripInfo() {
	return tripInfo;
  }
}
