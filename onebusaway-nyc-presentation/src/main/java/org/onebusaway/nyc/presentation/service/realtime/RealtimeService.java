package org.onebusaway.nyc.presentation.service.realtime;

import java.util.List;

import org.onebusaway.nyc.siri.support.SiriJsonSerializer;
import org.onebusaway.nyc.siri.support.SiriXmlSerializer;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;

import uk.org.siri.siri.MonitoredStopVisitStructure;
import uk.org.siri.siri.VehicleActivityStructure;

public interface RealtimeService {

  public void setTime(long time);

  public PresentationService getPresentationService();
  
  public SiriJsonSerializer getSiriJsonSerializer();
  
  public SiriXmlSerializer getSiriXmlSerializer();
  
  
  public VehicleActivityStructure getVehicleActivityForVehicle(String vehicleId, 
      int maximumOnwardCalls, long currentTime, boolean showApc);
  
  public List<VehicleActivityStructure> getVehicleActivityForRoute(String routeId, 
	      String directionId, int maximumOnwardCalls, long currentTime, boolean showApc);
	    
  public List<MonitoredStopVisitStructure> getMonitoredStopVisitsForStop(String stopId, 
      int maximumOnwardCalls, long currentTime, boolean showApc);

  
  public boolean getVehiclesInServiceForRoute(String routeId, String directionId, long currentTime);

  public boolean getVehiclesInServiceForStopAndRoute(String stopId, String routeId, long currentTime);

  
  // FIXME TODO: refactor these to receive a passed in collection of MonitoredStopVisits or VehicleActivities?
  public List<ServiceAlertBean> getServiceAlertsForRoute(String routeId);

  public List<ServiceAlertBean> getServiceAlertsForRouteAndDirection(
      String routeId, String directionId);
  
  public List<ServiceAlertBean> getServiceAlertsGlobal();
    
}