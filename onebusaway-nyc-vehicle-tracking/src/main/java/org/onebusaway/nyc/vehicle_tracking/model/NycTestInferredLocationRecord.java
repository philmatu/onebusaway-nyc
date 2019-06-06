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
package org.onebusaway.nyc.vehicle_tracking.model;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;
import org.onebusaway.nyc.vehicle_tracking.model.csv.AgencyAndIdFieldMappingFactory;
import org.onebusaway.nyc.vehicle_tracking.model.csv.DateTimeFieldMappingFactory;

import com.google.common.base.Strings;

import java.io.Serializable;
import java.util.Date;

public class NycTestInferredLocationRecord implements Serializable {

  private static final long serialVersionUID = 1L;

  private static final int MISSING_VALUE = -999;

  /****
   * Raw Observation Values
   ****/
  @CsvField(name = "vid", mapping = AgencyAndIdFieldMappingFactory.class)
  private AgencyAndId vehicleId;

  @CsvField(optional = true)
  private Integer recordNumber;

  /**
   * Time record was received by us.
   */
  @CsvField(name = "timestamp", mapping = DateTimeFieldMappingFactory.class)
  private long timestamp;
  
  @CsvField(optional = true)
  private double lat;

  @CsvField(optional = true)
  private double lon;

  @CsvField(optional = true)
  private String operatorId;

  @CsvField(optional = true)
  private String reportedRunId;

  @CsvField(optional = true)
  private String assignedRunId;

  @CsvField(optional = true)
  private String dsc;

  @CsvField(optional = true)
  private Boolean actualIsRunFormal;

  @CsvField(optional = true)
  private Boolean inferredIsRunFormal;
  
  @CsvField(optional = true)
  private String actualPhase = null;

  @CsvField(optional = true)
  private String inferredPhase = null;

  @CsvField(optional = true)
  private String actualDsc;

  @CsvField(optional = true)
  private String inferredDsc;

  @CsvField(optional = true)
  private String actualStatus = null;

  @CsvField(optional = true)
  private String inferredStatus = null;

  @CsvField(optional = true)
  private String actualRunId;

  @CsvField(optional = true)
  private String inferredRunId;

  @CsvField(optional = true)
  private String actualTripId;
  
  @CsvField(optional = true)
  private String inferredTripId;

  @CsvField(optional = true)
  private String actualBlockId;

  @CsvField(optional = true)
  private String inferredBlockId;

  @CsvField(optional = true)
  private String assignedBlockId;

  @CsvField(optional = true)
  private double actualScheduleDeviation;

  @CsvField(optional = true)
  private double inferredScheduleDeviation;

  @CsvField(optional = true)
  private double actualDistanceAlongBlock = Double.NaN;
  
  @CsvField(optional = true)
  private double inferredDistanceAlongBlock = Double.NaN;

  @CsvField(optional = true, mapping = StopTimeFieldMappingFactory.class)
  private int actualScheduleTime = MISSING_VALUE;

  @CsvField(optional = true, mapping = StopTimeFieldMappingFactory.class)
  private int inferredScheduleTime = MISSING_VALUE;

  @CsvField(optional = true)
  private long actualServiceDate;

  @CsvField(optional = true)
  private long inferredServiceDate;

  @CsvField(optional = true)
  private double actualBlockLat = Double.NaN; 
  
  @CsvField(optional = true)
  private double inferredBlockLat = Double.NaN;

  @CsvField(optional = true)
  private double actualBlockLon = Double.NaN;

  @CsvField(optional = true)
  private double inferredBlockLon = Double.NaN;

  /****
   * Stats debugging information
   ****/
  @CsvField(optional = true, ignore=true)
  private String inferredStateMean = null;
  
  @CsvField(optional = true, ignore=true)
  private String inferredStateCovariance = null;
  
  @CsvField(optional = true, ignore=true)
  private String inferredEdge = null;

  // **************

  public void setVehicleId(AgencyAndId vehicleId) {
    this.vehicleId = vehicleId;
  }

  public AgencyAndId getVehicleId() {
    return vehicleId;
  }

  public String getOperatorId() {
    return operatorId;
  }

  public void setLat(double lat) {
    this.lat = lat;
  }

  public double getLat() {
    return lat;
  }

  public void setLon(double lon) {
    this.lon = lon;
  }

  public double getLon() {
    return lon;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Date getTimestampAsDate() {
    return new Date(timestamp);
  }

  public void setDsc(String dsc) {
    this.dsc = dsc;
  }

  public String getDsc() {
    return dsc;
  }

  /****
   * Inferred Values
   ****/
  public String getInferredRunId() {
    return inferredRunId;
  }

  public void setInferredRunId(String inferredRunId) {
    this.inferredRunId = inferredRunId;
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

  public boolean isInferredServiceDateSet() {
    return inferredServiceDate > 0;
  }

  public long getInferredServiceDate() {
    return inferredServiceDate;
  }

  public void setInferredServiceDate(long inferredServiceDate) {
    this.inferredServiceDate = inferredServiceDate;
  }

  public boolean isInferredDistanceAlongBlockSet() {
    return !Double.isNaN(inferredDistanceAlongBlock);
  }

  public double getInferredDistanceAlongBlock() {
    return inferredDistanceAlongBlock;
  }

  public void setInferredDistanceAlongBlock(double inferredDistanceAlongBlock) {
    this.inferredDistanceAlongBlock = inferredDistanceAlongBlock;
  }

  public boolean isInferredScheduleTimeSet() {
    return inferredScheduleTime != MISSING_VALUE;
  }

  public int getInferredScheduleTime() {
    return inferredScheduleTime;
  }

  public void setInferredScheduleTime(int inferredScheduleTime) {
    this.inferredScheduleTime = inferredScheduleTime;
  }

  public String getInferredDsc() {
    return inferredDsc;
  }

  public void setInferredDsc(String inferredDsc) {
    this.inferredDsc = inferredDsc;
  }

  public boolean isInferredBlockLatSet() {
    return !Double.isNaN(inferredBlockLat);
  }

  public double getInferredBlockLat() {
    return inferredBlockLat;
  }

  public void setInferredBlockLat(double inferredBlockLat) {
    this.inferredBlockLat = inferredBlockLat;
  }

  public boolean isInferredBlockLonSet() {
    return !Double.isNaN(inferredBlockLon);
  }

  public double getInferredBlockLon() {
    return inferredBlockLon;
  }

  public void setInferredBlockLon(double inferredBlockLon) {
    this.inferredBlockLon = inferredBlockLon;
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

  public void clearInferredValues() {
    inferredRunId = null;
    inferredBlockId = null;
    inferredBlockLat = Double.NaN;
    inferredBlockLon = Double.NaN;
    inferredDistanceAlongBlock = Double.NaN;
    inferredDsc = null;
    inferredPhase = null;
    inferredScheduleTime = MISSING_VALUE;
    inferredServiceDate = 0;
    inferredStatus = null;
    inferredTripId = null;
  }

  /****
   * Ground Truth Data
   ****/
  public String getActualRunId() {
    return actualRunId;
  }

  public void setActualRunId(String actualRunId) {
    this.actualRunId = actualRunId;
  }

  public String getActualBlockId() {
    return actualBlockId;
  }

  public void setActualBlockId(String actualBlockId) {
    this.actualBlockId = actualBlockId;
  }

  public String getActualTripId() {
    return actualTripId;
  }

  public void setActualTripId(String actualTripId) {
    this.actualTripId = actualTripId;
  }

  public boolean isReportedRunInfoSet() {
    return (!Strings.isNullOrEmpty(this.reportedRunId) && !this.reportedRunId.matches("0*-?0*"))
        || (!Strings.isNullOrEmpty(this.operatorId) && !this.operatorId.matches("0*"));

  }

  public boolean isActualServiceDateSet() {
    return actualServiceDate > 0;
  }

  public long getActualServiceDate() {
    return actualServiceDate;
  }

  public void setActualServiceDate(long actualServiceDate) {
    this.actualServiceDate = actualServiceDate;
  }

  public boolean isActualDistanceAlongBlockSet() {
    return !Double.isNaN(actualDistanceAlongBlock);
  }

  public double getActualDistanceAlongBlock() {
    return actualDistanceAlongBlock;
  }

  public void setActualDistanceAlongBlock(double actualDistanceAlongBlock) {
    this.actualDistanceAlongBlock = actualDistanceAlongBlock;
  }

  public boolean isActualScheduleTimeSet() {
    return actualScheduleTime != MISSING_VALUE;
  }

  public int getActualScheduleTime() {
    return actualScheduleTime;
  }

  public void setActualScheduleTime(int actualScheduleTime) {
    this.actualScheduleTime = actualScheduleTime;
  }

  public String getActualDsc() {
    return actualDsc;
  }

  public void setActualDsc(String actualDsc) {
    this.actualDsc = actualDsc;
  }

  public boolean isActualBlockLatSet() {
    return !Double.isNaN(actualBlockLat);
  }

  public double getActualBlockLat() {
    return actualBlockLat;
  }

  public void setActualBlockLat(double actualBlockLat) {
    this.actualBlockLat = actualBlockLat;
  }

  public boolean isActualBlockLonSet() {
    return !Double.isNaN(actualBlockLon);
  }

  public double getActualBlockLon() {
    return actualBlockLon;
  }

  public void setActualBlockLon(double actualBlockLon) {
    this.actualBlockLon = actualBlockLon;
  }

  public String getActualPhase() {
    return actualPhase;
  }

  public void setActualPhase(String actualPhase) {
    this.actualPhase = actualPhase;
  }

  public String getActualStatus() {
    return actualStatus;
  }

  public void setActualStatus(String actualStatus) {
    this.actualStatus = actualStatus;
  }

  /**
   * Location data is considered missing if the values are NaN or if both are
   * zero
   */
  public boolean locationDataIsMissing() {
    return (Double.isNaN(this.lat) || Double.isNaN(this.lon))
        || (this.lat == 0.0 && this.lon == 0.0);
  }

  public void setOperatorId(String operatorId) {
    this.operatorId = operatorId;

  }

  public String getReportedRunId() {
    return reportedRunId;
  }

  public void setReportedRunId(String reportedRunId) {
    this.reportedRunId = reportedRunId;
  }

  public Integer getRecordNumber() {
    return recordNumber;
  }

  public void setRecordNumber(Integer recordNumber) {
    this.recordNumber = recordNumber;
  }

  public double getActualScheduleDeviation() {
    return actualScheduleDeviation;
  }

  public void setActualScheduleDeviation(double actualScheduleDeviation) {
    this.actualScheduleDeviation = actualScheduleDeviation;
  }

  public double getInferredScheduleDeviation() {
    return inferredScheduleDeviation;
  }

  public void setInferredScheduleDeviation(double inferredScheduleDeviation) {
    this.inferredScheduleDeviation = inferredScheduleDeviation;
  }

  public Boolean getInferredIsRunFormal() {
    return inferredIsRunFormal;
  }

  public void setInferredIsRunFormal(Boolean isRunFormal) {
    this.inferredIsRunFormal = isRunFormal;
  }

  public Boolean getActualIsRunFormal() {
    return actualIsRunFormal;
  }

  public void setActualIsRunFormal(Boolean isRunFormal) {
    this.actualIsRunFormal = isRunFormal;
  }

  public String getAssignedRunId() {
    return assignedRunId;
  }

  public void setAssignedRunId(String assignedRunId) {
    this.assignedRunId = assignedRunId;
  }

  public String getAssignedBlockId() {
    return assignedBlockId;
  }

  public void setAssignedBlockId(String assignedBlockId) {
    this.assignedBlockId = assignedBlockId;
  }
}
