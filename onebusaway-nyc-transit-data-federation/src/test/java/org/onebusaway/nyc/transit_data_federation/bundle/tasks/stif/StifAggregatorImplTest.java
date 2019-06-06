package org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.onebusaway.gtfs.impl.GtfsRelationalDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.MultiCSVLogger;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.StifTripLoader;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.impl.AbnormalStifDataLoggerImpl;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.impl.StifAggregatorImpl;
import org.onebusaway.nyc.transit_data_federation.bundle.tasks.stif.impl.StifLoaderImpl;
import org.onebusaway.nyc.transit_data_federation.model.nyc.SupplimentalTripInformation;
import static org.mockito.Mockito.*;


public class StifAggregatorImplTest {
  final static Logger _log = Logger.getRootLogger();
  
  @Before
  public void setUp() throws Exception {
    BasicConfigurator.configure();
    _log.setLevel(Level.DEBUG);
  }

  
  @Test
  public void testComputeBlocksFromRuns() {
    StifAggregatorImpl agg = new StifAggregatorImpl();
    StifLoaderImpl sl = new StifLoaderImpl();
    MultiCSVLogger logger = new MultiCSVLogger();

    AbnormalStifDataLoggerImpl a = new AbnormalStifDataLoggerImpl();
    a.setLogger(logger);
    
    InputStream in = getClass().getResourceAsStream("stif.q_0004__.516151.sun");
    String gtfs = getClass().getResource("q4_gtfs").getFile();

    GtfsReader reader = new GtfsReader();
    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
    reader.setEntityStore(dao);
    sl.setGtfsMutableRelationalDao(dao);
    
    try {
      reader.setInputLocation(new File(gtfs));
      reader.run();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Exception when loading GTFS");
    }
    
    StifTripLoader loader = getStifTripLoader(logger, dao);
    loader.run(in, new File("stif.q_0004__.516151.sun"));
    
    sl.setStifTripLoader(loader);
    agg.setStifLoader(sl);
          
    agg.setAbnormalStifDataLoggerImpl(a);
    
    agg.computeBlocksFromRuns();
    
    
    
    assertEquals(174, agg.getMatchedGtfsTripsCount());
    assertEquals(1,agg.getRoutesWithTripsCount());
    assertEquals(0,agg.getUnmatchedTripsSize());
    
    HashMap<AgencyAndId, SupplimentalTripInformation> suppTripInfo = agg.getSupplimentalTripInfo();
    SupplimentalTripInformation suppTrip = suppTripInfo.get(new AgencyAndId("MTA NYCT", "JA_A6-Sunday-001800_Q4_1"));
    
    assertEquals("E", suppTrip.getDirection());
    assertEquals(StifTripType.REVENUE, suppTrip.getTripType());
    assertEquals("R".charAt(0), suppTrip.getBusType());
  }
  
  
// This test uses a STIF that interlines with another (bx23). 
  @Test
  public void testComputeBlocksFromInterlinedRuns() {
    StifAggregatorImpl agg = new StifAggregatorImpl();
    StifLoaderImpl sl = new StifLoaderImpl();
    MultiCSVLogger logger = new MultiCSVLogger();
    
    String stifFile = "stif.Q_50____.714166.wkd.open";
    String stifFile2 = "stif.BXBX23__.714167.wkd.open";
    String gtfsDir = "q50_gtfs";
    
    String gtfs = getClass().getResource(gtfsDir).getFile();

    GtfsReader reader = new GtfsReader();
    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
    reader.setEntityStore(dao);
    sl.setGtfsMutableRelationalDao(dao);
    
    try {
      reader.setInputLocation(new File(gtfs));
      reader.run();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Exception when loading GTFS");
    }
    
    StifTripLoader loader = getStifTripLoader(logger, dao);
    InputStream in = getClass().getResourceAsStream(stifFile);
    loader.run(in, new File(stifFile));
    InputStream in2 = getClass().getResourceAsStream(stifFile2);
    loader.run(in2, new File(stifFile2));
    
    sl.setStifTripLoader(loader);
    agg.setStifLoader(sl);
       
    AbnormalStifDataLoggerImpl a = new AbnormalStifDataLoggerImpl();
    a.setLogger(logger);
    
    agg.setAbnormalStifDataLoggerImpl(a);
    
    agg.computeBlocksFromRuns();
    
    assertEquals(255, agg.getMatchedGtfsTripsCount());
    assertEquals(2,agg.getRoutesWithTripsCount());

  }
  
  @Test
  public void testComputeBlocksFromRunsB3() {
    StifAggregatorImpl agg = new StifAggregatorImpl();
    StifLoaderImpl sl = new StifLoaderImpl();

    AbnormalStifDataLoggerImpl a = new AbnormalStifDataLoggerImpl();
    MultiCSVLogger logger = new MultiCSVLogger();
    a.setLogger(logger);
    
    String gtfsDir = "b3_gtfs";
    
    String gtfs = getClass().getResource(gtfsDir).getFile();
    
    String stifs = getClass().getResource("b3_stifs").getFile();
    File stifFiles = new File(stifs);
    
    GtfsReader reader = new GtfsReader();
    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
    reader.setEntityStore(dao);
    sl.setGtfsMutableRelationalDao(dao);
       
    try {
      reader.setInputLocation(new File(gtfs));
      reader.run();
      _log.debug("dao has " + dao.getAllTrips().size() + " trips");
    } catch (IOException e) {
      e.printStackTrace();
      fail("Exception when loading GTFS");
    }
    
    StifTripLoader loader = getStifTripLoader(new MultiCSVLogger(), dao);
   
    sl.setStifTripLoader(loader);
    agg.setStifLoader(sl);
    agg.setAbnormalStifDataLoggerImpl(a);
    sl.loadStif(stifFiles, loader);
       
    agg.setAbnormalStifDataLoggerImpl(a);
    
    agg.computeBlocksFromRuns();
    
    assert(agg.getMatchedGtfsTripsCount() > 0);
    assertEquals(4,agg.getRoutesWithTripsCount());
    assertEquals(0,agg.getUnmatchedTripsSize());
  }
  
  /*
   * This test includes 12 blocks without pullouts-- they are in the as-assigned set
   *  
   */
  @Test
  public void testComputeBlocksFromRunsMTABC() {
    StifAggregatorImpl agg = new StifAggregatorImpl();
    StifLoaderImpl sl = new StifLoaderImpl();
    AbnormalStifDataLoggerImpl a = new AbnormalStifDataLoggerImpl();
    MultiCSVLogger logger = new MultiCSVLogger();
    a.setLogger(logger);
    
    String gtfsDir = "mtabc_b4_gtfs";
        
    String gtfs = getClass().getResource(gtfsDir).getFile();
    
    GtfsReader reader = new GtfsReader();
    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
    reader.setEntityStore(dao);
    sl.setGtfsMutableRelationalDao(dao);
    sl.setAbnormalStifDataLoggerImpl(a);
       
    try {
      reader.setInputLocation(new File(gtfs));
      reader.run();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Exception when loading GTFS");
    }
    
    StifTripLoader loader = getStifTripLoader(logger, dao);
    sl.setStifTripLoader(loader);    
   
    String stifs = getClass().getResource("mtabc_b4_stif").getFile();
    File stifFiles = new File(stifs);
    
    agg.setAbnormalStifDataLoggerImpl(a);
    sl.loadStif(stifFiles, loader);
    agg.setStifLoader(sl);
    
    
    agg.computeBlocksFromRuns();
    
    assert(agg.getMatchedGtfsTripsCount() > 0);
    assertEquals(18,agg.getRoutesWithTripsCount());
    assertEquals(12,agg.getUnmatchedTripsSize());
  }

  /*
   * This one should have no problems. 
   */
  @Test
  public void testComputeBlocksFromRunsBronxNewYears() {
    StifAggregatorImpl agg = new StifAggregatorImpl();
    StifLoaderImpl sl = new StifLoaderImpl();
    AbnormalStifDataLoggerImpl a = new AbnormalStifDataLoggerImpl();
    MultiCSVLogger logger = new MultiCSVLogger();
    a.setLogger(logger);
    
    String gtfsDir = "2013Sept_Bronx_gtfs";
        
    String gtfs = getClass().getResource(gtfsDir).getFile();
    
    GtfsReader reader = new GtfsReader();
    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
    reader.setEntityStore(dao);
    sl.setGtfsMutableRelationalDao(dao);
    sl.setAbnormalStifDataLoggerImpl(a);
       
    try {
      reader.setInputLocation(new File(gtfs));
      reader.run();
    } catch (IOException e) {
      e.printStackTrace();
      fail("Exception when loading GTFS");
    }
    
    StifTripLoader loader = getStifTripLoader(logger, dao);
    sl.setStifTripLoader(loader);    
   
    String stifs = getClass().getResource("2013Sept_Bronx_stif").getFile();
    File stifFiles = new File(stifs);
    
    agg.setAbnormalStifDataLoggerImpl(a);
    sl.loadStif(stifFiles, loader);
    agg.setStifLoader(sl);
    agg.computeBlocksFromRuns();
    
    assert(agg.getMatchedGtfsTripsCount() > 0);
    assertEquals(42,agg.getRoutesWithTripsCount());
    assertEquals(0,agg.getUnmatchedTripsSize());
    
    HashMap<AgencyAndId, SupplimentalTripInformation> suppTripInfo = agg.getSupplimentalTripInfo();
    SupplimentalTripInformation suppTrip = suppTripInfo.get(new AgencyAndId("MTA NYCT", "KB_Y4-Weekday-033500_BX1_103"));
    
    assertEquals("S", suppTrip.getDirection());
    assertEquals(StifTripType.REVENUE, suppTrip.getTripType());
    assertEquals("A".charAt(0), suppTrip.getBusType());
    
    suppTrip = suppTripInfo.get(new AgencyAndId("MTA NYCT", "GH_Y4-Weekday-133000_BX238_38"));
    
    assertEquals("E", suppTrip.getDirection());
    assertEquals(StifTripType.REVENUE, suppTrip.getTripType());
    assertEquals("R".charAt(0), suppTrip.getBusType());
  }

  
  @Test
  public void testComputeBlocksFromRunsBronxSept() {
	    StifAggregatorImpl agg = new StifAggregatorImpl();
	    StifLoaderImpl sl = new StifLoaderImpl();
	    AbnormalStifDataLoggerImpl a = new AbnormalStifDataLoggerImpl();
	    MultiCSVLogger logger = new MultiCSVLogger();
	    a.setLogger(logger);
	    
	    String gtfsDir = "2013Sept_Bronx_All_gtfs";
	        
	    String gtfs = getClass().getResource(gtfsDir).getFile();
	    
	    GtfsReader reader = new GtfsReader();
	    GtfsRelationalDaoImpl dao = new GtfsRelationalDaoImpl();
	    reader.setEntityStore(dao);
	    sl.setGtfsMutableRelationalDao(dao);
	    sl.setAbnormalStifDataLoggerImpl(a);
	       
	    try {
	      reader.setInputLocation(new File(gtfs));
	      reader.run();
	    } catch (IOException e) {
	      e.printStackTrace();
	      fail("Exception when loading GTFS");
	    }
	    
	    StifTripLoader loader = getStifTripLoader(logger, dao);
	    sl.setStifTripLoader(loader);    
	   
	    String stifs = getClass().getResource("2013Sept_Bronx_All_stif").getFile();
	    File stifFiles = new File(stifs);
	    
	    agg.setAbnormalStifDataLoggerImpl(a);
	    sl.loadStif(stifFiles, loader);
	    agg.setStifLoader(sl);
	    agg.computeBlocksFromRuns();
	    
	    assert(agg.getMatchedGtfsTripsCount() > 0);
	    assertEquals(1,agg.getRoutesWithTripsCount());
	    assertEquals(0,agg.getUnmatchedTripsSize());
	    
	  }
  
  //@Test
  public void testAddToSupplimentalTripInfo(){
	  StifAggregatorImpl a = new StifAggregatorImpl();
	  
	  AgencyAndId tripId = new AgencyAndId("A", "B");
	
	  //TBD find out how to mock Trips
	  Trip mockTrip = mock(Trip.class);
	  when(mockTrip.getId()).thenReturn(tripId);
	  
	  StifTrip mockStifTrip = new StifTrip("run1", "run1", "run2", StifTripType.REVENUE, "1234", "R".charAt(0), "E");
	  a.addToSupplimentalTripInfo(mockTrip, mockStifTrip);
	  
	  SupplimentalTripInformation suppInfo = a.getSupplimentalTripInfo().get(tripId);
	  assertEquals(suppInfo.getDirection(), "E");
	  assertEquals(suppInfo.getBusType(), "R".charAt(0));
  }

  
  private StifTripLoader getStifTripLoader(MultiCSVLogger logger,
      GtfsRelationalDaoImpl dao) {
    StifTripLoader loader = new StifTripLoader();
    loader.setLogger(logger);
    loader.setGtfsDao(dao);
    return loader;
  }
  
}
