/*
 * Copyright 2010, OpenPlans Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.nyc.webapp.actions.api.siri;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.onebusaway.geospatial.model.CoordinateBounds;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.presentation.impl.service_alerts.ServiceAlertsHelper;
import org.onebusaway.nyc.presentation.service.realtime.RealtimeService;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.nyc.webapp.actions.OneBusAwayNYCActionSupport;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.VehicleStatusBean;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.nyc.presentation.service.cache.NycCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import uk.org.siri.siri.ErrorDescriptionStructure;
import uk.org.siri.siri.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri.OtherErrorStructure;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceDeliveryErrorConditionStructure;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.VehicleActivityStructure;
import uk.org.siri.siri.VehicleMonitoringDeliveryStructure;

@ParentPackage("onebusaway-webapp-api")
public class VehicleMonitoringAction extends OneBusAwayNYCActionSupport
    implements ServletRequestAware, ServletResponseAware {

  private static final long serialVersionUID = 1L;
  protected static Logger _log = LoggerFactory.getLogger(VehicleMonitoringAction.class);
  
  @Autowired
  public NycTransitDataService _nycTransitDataService;

  @Autowired
  private RealtimeService _realtimeService;

  @Autowired
  protected ConfigurationService _configurationService;
  
  private static final String VERSION = "V1";
  
  private Siri _response;
  
  private String _cachedResponse = null;

  private ServiceAlertsHelper _serviceAlertsHelper = new ServiceAlertsHelper();

  HttpServletRequest _request;
  
  private HttpServletResponse _servletResponse;

  // See urlrewrite.xml as to how this is set. Which means this action doesn't
  // respect an HTTP Accept: header.
  private String _type = "xml";

  @Resource(name="siriCacheService")
  private NycCacheService<Integer, String> _siriCacheService;
  
  private MonitoringActionSupport _monitoringActionSupport = new MonitoringActionSupport();
  
  public void setType(String type) {
    _type = type;
  }

  @Override
  public String execute() {

    long currentTimestamp = getTime();
    _monitoringActionSupport.setupGoogleAnalytics(_request, _configurationService);
    
    _realtimeService.setTime(currentTimestamp);
    
    String directionId = _request.getParameter("DirectionRef");
    
    // We need to support the user providing no agency id which means 'all agencies'.
    // So, this array will hold a single agency if the user provides it or all
    // agencies if the user provides none. We'll iterate over them later while 
    // querying for vehicles and routes
    List<String> agencyIds = new ArrayList<String>();

    String agencyId = _request.getParameter("OperatorRef");
    
    if (agencyId != null) {
      agencyIds.add(agencyId);
    } else {
      Map<String,List<CoordinateBounds>> agencies = _nycTransitDataService.getAgencyIdsWithCoverageArea();
      agencyIds.addAll(agencies.keySet());
    }

    List<AgencyAndId> vehicleIds = new ArrayList<AgencyAndId>();
    if (_request.getParameter("VehicleRef") != null) {
      try {
        // If the user included an agency id as part of the vehicle id, ignore any OperatorRef arg
        // or lack of OperatorRef arg and just use the included one.
        AgencyAndId vehicleId = AgencyAndIdLibrary.convertFromString(_request.getParameter("VehicleRef"));
        vehicleIds.add(vehicleId);
      } catch (Exception e) {
        // The user didn't provide an agency id in the VehicleRef, so use our list of operator refs
        for (String agency : agencyIds) {
          AgencyAndId vehicleId = new AgencyAndId(agency, _request.getParameter("VehicleRef"));
          vehicleIds.add(vehicleId);
        }
      }
    }
    
    List<AgencyAndId> routeIds = new ArrayList<AgencyAndId>();
    String routeIdErrorString = "";
    if (_request.getParameter("LineRef") != null) {
      try {
        // Same as above for vehicle id
        AgencyAndId routeId = AgencyAndIdLibrary.convertFromString(_request.getParameter("LineRef"));
        if (isValidRoute(routeId)) {
          routeIds.add(routeId);
        } else {
          routeIdErrorString += "No such route: " + routeId.toString() + ".";
        }
      } catch (Exception e) {
        // Same as above for vehicle id
        for (String agency : agencyIds) {
          AgencyAndId routeId = new AgencyAndId(agency, _request.getParameter("LineRef"));
          if (isValidRoute(routeId)) {
            routeIds.add(routeId);
          } else {
            routeIdErrorString += "No such route: " + routeId.toString() + ". ";
          }
        }
      }
    }

    String detailLevel = _request.getParameter("VehicleMonitoringDetailLevel");

    int maximumOnwardCalls = 0;
    if (detailLevel != null && detailLevel.equals("calls")) {
      maximumOnwardCalls = Integer.MAX_VALUE;

      try {
        maximumOnwardCalls = Integer.parseInt(_request.getParameter("MaximumNumberOfCallsOnwards"));
      } catch (NumberFormatException e) {
        maximumOnwardCalls = Integer.MAX_VALUE;
      }
    }

    boolean showApc = Boolean.parseBoolean(_request.getParameter("ShowApc"));

    String gaLabel = null;
    
    // *** CASE 1: single vehicle, ignore any other filters
    if (vehicleIds.size() > 0) {
      
      gaLabel = _request.getParameter("VehicleRef");
      
      List<VehicleActivityStructure> activities = new ArrayList<VehicleActivityStructure>();
      
      for (AgencyAndId vehicleId : vehicleIds) {
        VehicleActivityStructure activity = _realtimeService.getVehicleActivityForVehicle(
            vehicleId.toString(), maximumOnwardCalls, currentTimestamp, showApc);

        if (activity != null) {
          activities.add(activity);
        }
      }
      
      // No vehicle id validation, so we pass null for error
      _response = generateSiriResponse(activities, null, null, currentTimestamp);

      // *** CASE 2: by route, using direction id, if provided
    } else if (_request.getParameter("LineRef") != null) {
      
      gaLabel = _request.getParameter("LineRef");
      
      List<VehicleActivityStructure> activities = new ArrayList<VehicleActivityStructure>();
      
      for (AgencyAndId routeId : routeIds) {
        
        List<VehicleActivityStructure> activitiesForRoute = _realtimeService.getVehicleActivityForRoute(
            routeId.toString(), directionId, maximumOnwardCalls, currentTimestamp, showApc);
        if (activitiesForRoute != null) {
          activities.addAll(activitiesForRoute);
        }
      }
      
      if (vehicleIds.size() > 0) {
        List<VehicleActivityStructure> filteredActivities = new ArrayList<VehicleActivityStructure>();

        for (VehicleActivityStructure activity : activities) {
          MonitoredVehicleJourneyStructure journey = activity.getMonitoredVehicleJourney();
          AgencyAndId thisVehicleId = AgencyAndIdLibrary.convertFromString(journey.getVehicleRef().getValue());

          // user filtering
          if (!vehicleIds.contains(thisVehicleId))
            continue;

          filteredActivities.add(activity);
        }

        activities = filteredActivities;
      }
      
      Exception error = null;
      if (_request.getParameter("LineRef") != null && routeIds.size() == 0) {
        error = new Exception(routeIdErrorString.trim());
      }

      _response = generateSiriResponse(activities, routeIds, error, currentTimestamp);
      
      // *** CASE 3: all vehicles
    } else {
      try {
      gaLabel = "All Vehicles";
      
      int hashKey = _siriCacheService.hash(maximumOnwardCalls, agencyIds, _type, VERSION);
      
      List<VehicleActivityStructure> activities = new ArrayList<VehicleActivityStructure>();
      if (!_siriCacheService.containsKey(hashKey)) {
        for (String agency : agencyIds) {
          ListBean<VehicleStatusBean> vehicles = _nycTransitDataService.getAllVehiclesForAgency(
                    agency, currentTimestamp);

          for (VehicleStatusBean v : vehicles.getList()) {
              VehicleActivityStructure activity = _realtimeService.getVehicleActivityForVehicle(
                      v.getVehicleId(), maximumOnwardCalls, currentTimestamp, showApc);
              if (activity != null) {
                activities.add(activity);
              }
          }
        }
        // There is no input (route id) to validate, so pass null error
        _response = generateSiriResponse(activities, null, null,
            currentTimestamp);
        _siriCacheService.store(hashKey, getVehicleMonitoring());
      } else {
        _cachedResponse = _siriCacheService.retrieve(hashKey);
      }
      } catch (Exception e) {
        _log.error("vm all broke:", e);
        throw new RuntimeException(e);
      }
    }
    
    if (_monitoringActionSupport.canReportToGoogleAnalytics(_configurationService)) {
      _monitoringActionSupport.reportToGoogleAnalytics(_request, "Vehicle Monitoring", gaLabel, _configurationService);
    } 
    
    try {
      this._servletResponse.getWriter().write(getVehicleMonitoring());
    } catch (Throwable t) {
      _log.error("exception in vehicle monitoring:", t);
    }

    return null;
  }

  /**
   * Generate a siri response for a set of VehicleActivities
   * 
   * @param routeId
   */
  private Siri generateSiriResponse(List<VehicleActivityStructure> activities,
      List<AgencyAndId> routeIds, Exception error, long currentTimestamp) {
    
    VehicleMonitoringDeliveryStructure vehicleMonitoringDelivery = new VehicleMonitoringDeliveryStructure();
    vehicleMonitoringDelivery.setResponseTimestamp(new Date(currentTimestamp));
    
    ServiceDelivery serviceDelivery = new ServiceDelivery();
    serviceDelivery.setResponseTimestamp(new Date(currentTimestamp));
    serviceDelivery.getVehicleMonitoringDelivery().add(vehicleMonitoringDelivery);
    
    if (error != null) {
      ServiceDeliveryErrorConditionStructure errorConditionStructure = new ServiceDeliveryErrorConditionStructure();
      
      ErrorDescriptionStructure errorDescriptionStructure = new ErrorDescriptionStructure();
      errorDescriptionStructure.setValue(error.getMessage());
      
      OtherErrorStructure otherErrorStructure = new OtherErrorStructure();
      otherErrorStructure.setErrorText(error.getMessage());
      
      errorConditionStructure.setDescription(errorDescriptionStructure);
      errorConditionStructure.setOtherError(otherErrorStructure);
      
      vehicleMonitoringDelivery.setErrorCondition(errorConditionStructure);
    } else {
      Calendar gregorianCalendar = new GregorianCalendar();
      gregorianCalendar.setTimeInMillis(currentTimestamp);
      gregorianCalendar.add(Calendar.MINUTE, 1);
      vehicleMonitoringDelivery.setValidUntil(gregorianCalendar.getTime());

      vehicleMonitoringDelivery.getVehicleActivity().addAll(activities);

      _serviceAlertsHelper.addSituationExchangeToServiceDelivery(serviceDelivery,
          activities, _nycTransitDataService, routeIds);
      _serviceAlertsHelper.addGlobalServiceAlertsToServiceDelivery(serviceDelivery, _realtimeService);
    }
    Siri siri = new Siri();
    siri.setServiceDelivery(serviceDelivery);

    return siri;
  }
  
  private boolean isValidRoute(AgencyAndId routeId) {
    if (routeId != null && routeId.hasValues() && this._nycTransitDataService.getRouteForId(routeId.toString()) != null) {
      return true;
    }
    return false;
  }

  public String getVehicleMonitoring() {
    if (_cachedResponse != null) {
      // check the cache first
      return _cachedResponse;
    }
    
    try {
      if (_type.equals("xml")) {
        this._servletResponse.setContentType("application/xml");
        return _realtimeService.getSiriXmlSerializer().getXml(_response);
      } else {
        this._servletResponse.setContentType("application/json");
        return _realtimeService.getSiriJsonSerializer().getJson(_response,
            _request.getParameter("callback"));
      }
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  @Override
  public void setServletRequest(HttpServletRequest request) {
    this._request = request;
  }

  @Override
  public void setServletResponse(HttpServletResponse servletResponse) {
    this._servletResponse = servletResponse;
  }
  
  public HttpServletResponse getServletResponse(){
    return _servletResponse;
  }
}
