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

/**
 * Hibernate entity for archiving most recent NycQueuedInferredLocationBeans,
 * NycVehicleManagementStatusBeans, and TDS data coming from the inference
 * engine queue.
 * 
 * @author smeeks
 * 
 */
package org.onebusaway.nyc.report.model;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.nyc.transit_data.model.NycQueuedInferredLocationBean;
import org.onebusaway.nyc.transit_data.model.NycVehicleManagementStatusBean;
import org.hibernate.annotations.AccessType;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Index;
import org.onebusaway.nyc.vehicle_tracking.model.csv.UTCDateTimeFieldMappingFactory;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "obanyc_inferredlocation")
@AccessType("field")
@Cache(usage = CacheConcurrencyStrategy.NONE)
public class ArchivedInferredLocationRecord implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(generator = "increment")
  @GenericGenerator(name = "increment", strategy = "increment")
  @AccessType("property")
  private Long id;

  // This will come from NycVehicleManagementStatusBean
  @Index(name = "UUID")
  @Column(nullable = false, name = "UUID", length = 36)
  @CsvField(name = "UUID")
  private String uuid;

  // Fields from NycQueuedInferredLocationBean
  @Column(nullable = false, name = "time_reported")
  @CsvField(mapping = UTCDateTimeFieldMappingFactory.class)
  private Date timeReported;

  @Column(nullable = false, name = "vehicle_id")
  @Index(name = "vehicle_id")
  private Integer vehicleId;

  @Column(nullable = false, name = "agency_id", length = 64)
  private String agencyId;

  // Matches archive_time_received in CcLocationReport
  @Column(nullable = false, name = "archive_time_received")
  @Index(name = "archive_time_received")
  @CsvField(mapping = UTCDateTimeFieldMappingFactory.class)
  private Date archiveTimeReceived;

  @Column(nullable = false, name = "service_date")
  @CsvField(mapping = UTCDateTimeFieldMappingFactory.class)
  private Date serviceDate;

  @Column(nullable = true, name = "schedule_deviation")
  private Integer scheduleDeviation;

  @Column(nullable = true, name = "inferred_block_id", length = 64)
  private String inferredBlockId;

  @Column(nullable = true, name = "inferred_trip_id", length = 64)
  private String inferredTripId;

  @Column(nullable = true, columnDefinition = "DECIMAL(14,6)", name = "distance_along_block")
  private Double distanceAlongBlock;

  @Column(nullable = true, columnDefinition = "DECIMAL(14,6)", name = "distance_along_trip")
  private Double distanceAlongTrip;

  @Column(nullable = true, columnDefinition = "DECIMAL(9,6)", name = "inferred_latitude")
  private BigDecimal inferredLatitude;

  @Column(nullable = true, columnDefinition = "DECIMAL(9,6)", name = "inferred_longitude")
  private BigDecimal inferredLongitude;

  @Column(nullable = false, name = "inferred_phase", length = 32)
  private String inferredPhase;

  @Column(nullable = false, name = "inferred_status", length = 32)
  private String inferredStatus;

  @Column(nullable = true, name = "inferred_route_id", length = 32)
  private String inferredRouteId;

  @Column(nullable = true, name = "inferred_direction_id", length = 1)
  private String inferredDirectionId;

  // Fields from NycVehicleManagementStatusBean
  @Column(nullable = false, name = "last_update_time")
  private Long lastUpdateTime;

  @Column(nullable = false, name = "last_location_update_time")
  private Long lastLocationUpdateTime;

  @Column(nullable = true, name = "inferred_dest_sign_code")
  private Integer inferredDestSignCode;

  @Column(nullable = false, name = "inference_is_formal", columnDefinition="BIT")
  private boolean inferenceIsFormal;

  @Column(nullable = false, name = "emergency_flag", columnDefinition="BIT")
  private boolean emergencyFlag;

  @Column(nullable = true, name = "depot_id", length = 16)
  private String depotId;

  @Column(nullable = true, name = "inferred_operator_id", length = 16)
  @CsvField(optional = true)
  private String inferredOperatorId;

  @Column(nullable = true, name = "inferred_run_id", length = 16)
  private String inferredRunId;

  // Fields from TDS
  // Stop ID of next scheduled stop
  @Column(nullable = true, name = "next_scheduled_stop_id", length = 32)
  private String nextScheduledStopId;

  // Distance to next scheduled stop
  @Column(nullable = true, columnDefinition = "DECIMAL(14,6)", name = "next_scheduled_stop_distance")
  private Double nextScheduledStopDistance;
  
  //Stop ID of previous scheduled stop
  @Column(nullable = true, name = "previous_scheduled_stop_id", length = 32)
  private String previousScheduledStopId;

  // Distance from previous scheduled stop
  @Column(nullable = true, columnDefinition = "DECIMAL(14,6)", name = "previous_scheduled_stop_distance")
  private Double previousScheduledStopDistance;

  @Column(nullable = true, name = "assigned_run_id", length = 16)
  private String assignedRunId = null;

  @Column(nullable = true, name = "assigned_block_id", length = 64)
  @CsvField(optional = true)
  private String assignedBlockId = null;

  public ArchivedInferredLocationRecord() {
  }

  public ArchivedInferredLocationRecord(NycQueuedInferredLocationBean message,
      String contents) {
    this(message, contents, System.currentTimeMillis());
  }
  
  public ArchivedInferredLocationRecord(NycQueuedInferredLocationBean message,
      String contents, long timeReceived) {
    super();

    Double possibleNaN;

    setTimeReported(new Date(message.getRecordTimestamp()));

    // Split vehicle id string to vehicle integer and agency designator string
    String id = message.getVehicleId();
    int index = id.indexOf('_');
    String agency = id.substring(0, index);
    int vehicleId = Integer.parseInt(id.substring(index + 1));

    setVehicleId(vehicleId);
    setAgencyId(agency);

    setArchiveTimeReceived(new Date(timeReceived));

    setServiceDate(new Date(message.getServiceDate()));
    setScheduleDeviation(message.getScheduleDeviation());

    possibleNaN = message.getDistanceAlongBlock();
    if (Double.isNaN(possibleNaN))
      setDistanceAlongBlock(null);
    else
      setDistanceAlongBlock(possibleNaN);

    setDistanceAlongTrip(message.getDistanceAlongTrip());

    if (Double.isNaN(message.getInferredLatitude()))
      setInferredLatitude(null);
    else
      setInferredLatitude(new BigDecimal(message.getInferredLatitude()));
    if (Double.isNaN(message.getInferredLongitude()))
      setInferredLongitude(null);
    else
      setInferredLongitude(new BigDecimal(message.getInferredLongitude()));
   
    setInferredPhase(message.getPhase());
    setInferredStatus(message.getStatus());
    setInferredRouteId(message.getRouteId());

    NycVehicleManagementStatusBean managementBean = message.getManagementRecord();

    setUUID(managementBean.getUUID());

    setLastUpdateTime(managementBean.getLastUpdateTime());
    String inferredDscString = managementBean.getLastInferredDestinationSignCode();
    if (inferredDscString != null) {
      setInferredDestSignCode(Integer.parseInt(inferredDscString));
    }
    setInferenceIsFormal(managementBean.isInferenceIsFormal());
    setDepotId(managementBean.getDepotId());
    setEmergencyFlag(managementBean.isEmergencyFlag());
    setInferredOperatorId(managementBean.getLastInferredOperatorId());
    setInferredRunId(managementBean.getInferredRunId());
    setAssignedRunId(managementBean.getAssignedRunId());
    setAssignedBlockId(managementBean.getAssignedBlockId());

    // TDS Fields
    setNextScheduledStopId(message.getNextScheduledStopId());
    setNextScheduledStopDistance(message.getNextScheduledStopDistance());
    setPreviousScheduledStopId(message.getPreviousScheduledStopId());
    setPreviousScheduledStopDistance(message.getPreviousScheduledStopDistance());
    setInferredBlockId(message.getInferredBlockId());
    setInferredTripId(message.getInferredTripId());
    setInferredRouteId(message.getInferredRouteId());
    setInferredDirectionId(message.getInferredDirectionId());
    setScheduleDeviation(message.getScheduleDeviation());
    
    if(message.getLastLocationUpdateTime() != null)
    	setLastLocationUpdateTime(message.getLastLocationUpdateTime());
    else
    	setLastLocationUpdateTime(managementBean.getLastLocationUpdateTime());
  }

  public NycQueuedInferredLocationBean toNycQueuedInferredLocationBean() {

    NycQueuedInferredLocationBean message = new NycQueuedInferredLocationBean();

    message.setRecordTimestamp(getTimeReported().getTime());

    // Split vehicle id string to vehicle integer and agency designator string
    message.setVehicleId(String.format("%s_%d", getAgencyId(), getVehicleId()));

    message.setServiceDate(getServiceDate().getTime());
    message.setScheduleDeviation(getScheduleDeviation());

    Double distance = getDistanceAlongBlock();
    if (distance == null)
      distance = Double.NaN;
    message.setDistanceAlongBlock(distance);

    message.setDistanceAlongTrip(getDistanceAlongTrip());

    BigDecimal latitude = getInferredLatitude();
    if (latitude == null)
      message.setInferredLatitude(Double.NaN);
    else
      message.setInferredLatitude(latitude.doubleValue());

    BigDecimal longitude = getInferredLongitude();
    if (longitude == null)
      message.setInferredLongitude(Double.NaN);
    else
      message.setInferredLongitude(longitude.doubleValue());

    message.setPhase(getInferredPhase());
    message.setStatus(getInferredStatus());
    message.setRouteId(getInferredRouteId());

    NycVehicleManagementStatusBean managementBean = new NycVehicleManagementStatusBean();
    managementBean.setUUID(getUUID());
    managementBean.setLastUpdateTime(getLastUpdateTime());

    Integer inferredDsc = getInferredDestSignCode();
    if (inferredDsc != null)
      managementBean.setLastInferredDestinationSignCode(inferredDsc.toString());
    managementBean.setInferenceIsFormal(isInferenceIsFormal());
    managementBean.setDepotId(getDepotId());
    managementBean.setEmergencyFlag(isEmergencyFlag());
    managementBean.setLastInferredOperatorId(getInferredOperatorId());
    managementBean.setInferredRunId(getInferredRunId());
    managementBean.setAssignedRunId(getAssignedRunId());
    managementBean.setAssignedBlockId(getAssignedBlockId());

    message.setManagementRecord(managementBean);

    // TDS Fields
    message.setNextScheduledStopId(getNextScheduledStopId());
    message.setNextScheduledStopDistance(getNextScheduledStopDistance());
    message.setPreviousScheduledStopId(getPreviousScheduledStopId());
    message.setPreviousScheduledStopDistance(getPreviousScheduledStopDistance());
    message.setInferredBlockId(getInferredBlockId());
    message.setInferredTripId(getInferredTripId());
    message.setInferredRouteId(getInferredRouteId());
    message.setInferredDirectionId(getInferredDirectionId());
    message.setScheduleDeviation(getScheduleDeviation());

    message.setLastLocationUpdateTime(getLastLocationUpdateTime());

    return message;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUUID() {
    return uuid;
  }

  public void setUUID(String uuid) {
    this.uuid = uuid;
  }

  // necessary for CSV deserialization
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public Date getTimeReported() {
    return timeReported;
  }

  public void setTimeReported(Date timeReported) {
    this.timeReported = timeReported;
  }

  public Integer getVehicleId() {
    return vehicleId;
  }

  public void setVehicleId(Integer vehicleId) {
    this.vehicleId = vehicleId;
  }

  public String getAgencyId() {
    return agencyId;
  }

  public void setAgencyId(String agencyId) {
    this.agencyId = agencyId;
  }

  public Date getArchiveTimeReceived() {
    return archiveTimeReceived;
  }

  public void setArchiveTimeReceived(Date archiveTimeReceived) {
    this.archiveTimeReceived = archiveTimeReceived;
  }

  public Date getServiceDate() {
    return serviceDate;
  }

  public void setServiceDate(Date serviceDate) {
    this.serviceDate = serviceDate;
  }

  public Integer getScheduleDeviation() {
    return scheduleDeviation;
  }

  public void setScheduleDeviation(Integer scheduleDeviation) {
    this.scheduleDeviation = scheduleDeviation;
  }

  public String getInferredBlockId() {
    return inferredBlockId;
  }

  public void setInferredBlockId(String inferredBlockId) {
    this.inferredBlockId = inferredBlockId;
  }

  public String getInferredTripId() {
    return inferredTripId;
  }

  public void setInferredTripId(String inferredTripId) {
    this.inferredTripId = inferredTripId;
  }

  public Double getDistanceAlongBlock() {
    return distanceAlongBlock;
  }

  public void setDistanceAlongBlock(Double distance) {
    this.distanceAlongBlock = distance;
  }

  public Double getDistanceAlongTrip() {
    return distanceAlongTrip;
  }

  public void setDistanceAlongTrip(Double distance) {
    this.distanceAlongTrip = distance;
  }

  public BigDecimal getInferredLatitude() {
    return inferredLatitude;
  }

  public void setInferredLatitude(BigDecimal latitude) {
    this.inferredLatitude = latitude;
  }

  public BigDecimal getInferredLongitude() {
    return inferredLongitude;
  }

  public void setInferredLongitude(BigDecimal longitude) {
    this.inferredLongitude = longitude;
  }

  public String getInferredPhase() {
    return inferredPhase;
  }

  public void setInferredPhase(String inferredPhase) {
    this.inferredPhase = inferredPhase;
  }

  public String getInferredStatus() {
    return inferredStatus;
  }

  public void setInferredStatus(String inferredStatus) {
    this.inferredStatus = inferredStatus;
  }

  public void setInferredRouteId(String inferredRouteId) {
    this.inferredRouteId = inferredRouteId;
  }

  public String getInferredRouteId() {
    return inferredRouteId;
  }

  public void setInferredDirectionId(String inferredDirectionId) {
    this.inferredDirectionId = inferredDirectionId;
  }

  public String getInferredDirectionId() {
    return inferredDirectionId;
  }

  // Properties from NycVehicleManagementStatusBean
  
  public long getLastUpdateTime() {
    return lastUpdateTime;
  }

  public void setLastUpdateTime(long lastUpdateTime) {
    this.lastUpdateTime = lastUpdateTime;
  }

  public long getLastLocationUpdateTime() {
    return lastLocationUpdateTime;
  }

  public void setLastLocationUpdateTime(long lastGpsTime) {
    this.lastLocationUpdateTime = lastGpsTime;
  }

  public Integer getInferredDestSignCode() {
    return inferredDestSignCode;
  }

  public void setInferredDestSignCode(Integer inferredDestSignCode) {
    this.inferredDestSignCode = inferredDestSignCode;
  }

  public boolean isInferenceIsFormal() {
    return inferenceIsFormal;
  }

  public void setInferenceIsFormal(boolean inferenceIsFormal) {
    this.inferenceIsFormal = inferenceIsFormal;
  }

  public void setDepotId(String depotId) {
    this.depotId = depotId;
  }

  public String getDepotId() {
    return depotId;
  }

  public boolean isEmergencyFlag() {
    return emergencyFlag;
  }

  public void setEmergencyFlag(boolean emergencyFlag) {
    this.emergencyFlag = emergencyFlag;
  }

  public void setInferredOperatorId(String operatorId) {
    this.inferredOperatorId = operatorId;
  }

  public String getInferredOperatorId() {
    return inferredOperatorId;
  }

  public void setInferredRunId(String inferredRunId) {
    this.inferredRunId = inferredRunId;
  }

  public String getInferredRunId() {
    return inferredRunId;
  }

  public void setAssignedRunId(String assignedRunId) {
    this.assignedRunId = assignedRunId;
  }

  public String getAssignedRunId() {
    return assignedRunId;
  }

  // Properties from TDS

  public void setNextScheduledStopId(String nextScheduledStopId) {
    this.nextScheduledStopId = nextScheduledStopId;
  }

  public String getNextScheduledStopId() {
    return nextScheduledStopId;
  }

  public void setNextScheduledStopDistance(Double distance) {
    this.nextScheduledStopDistance = distance;
  }

  public Double getNextScheduledStopDistance() {
    return nextScheduledStopDistance;
  }

public String getPreviousScheduledStopId() {
	return previousScheduledStopId;
}

public void setPreviousScheduledStopId(String previousScheduledStopId) {
	this.previousScheduledStopId = previousScheduledStopId;
}

public Double getPreviousScheduledStopDistance() {
	return previousScheduledStopDistance;
}

public void setPreviousScheduledStopDistance(
		Double previousScheduledStopDistance) {
	this.previousScheduledStopDistance = previousScheduledStopDistance;
}

public String getAssignedBlockId() {
  return assignedBlockId;
}

public void setAssignedBlockId(String assignedBlockId) {
  this.assignedBlockId = assignedBlockId;
}

}
