package org.onebusaway.nyc.transit_data_manager.adapters.input;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.onebusaway.nyc.transit_data_manager.adapters.input.model.MtaUtsVehiclePullInPullOut;
import org.onebusaway.nyc.transit_data_manager.adapters.tools.DepotIdTranslator;
import org.onebusaway.nyc.transit_data_manager.adapters.tools.UtsMappingTool;
import org.springframework.beans.factory.annotation.Autowired;

import tcip_final_3_0_5_1.CPTOperatorIden;
import tcip_final_3_0_5_1.CPTTransitFacilityIden;
import tcip_final_3_0_5_1.CPTVehicleIden;
import tcip_final_3_0_5_1.SCHBlockIden;
import tcip_final_3_0_5_1.SCHPullInOutInfo;

public class MtaUtsToTcipVehicleAssignmentConverter {
  
  private static String DATASOURCE_SYSTEM = "UTS"; 

  public MtaUtsToTcipVehicleAssignmentConverter() {
    
  }

  private DepotIdTranslator depotIdTranslator = null;
  
  UtsMappingTool mappingTool = null;

  @Autowired
  public void setMappingTool(UtsMappingTool mappingTool) {
    this.mappingTool = mappingTool;
  }

  /***
   * Need a class to generate pull out data from the schedule data of an input
   * row.
   * 
   * @param input a MtaUtsVehiclePullInPullOut, which contains both pull in and
   *          out scheduled times.
   * @return a SCHPullInOutInfo representing a pull out, using scheduled data.
   */
  public SCHPullInOutInfo convertToPullOut(MtaUtsVehiclePullInPullOut input) {
    return convertToGivenPullInOut(input, false);
  }

  /***
   * Need a class to generate pull in data from the schedule data of an input
   * row.
   * 
   * @param input a MtaUtsVehiclePullInPullOut, which contains both pull in and
   *          out scheduled times.
   * @return a SCHPullInOutInfo representing a pull in, using scheduled data.
   */
  public SCHPullInOutInfo convertToPullIn(MtaUtsVehiclePullInPullOut input) {
    return convertToGivenPullInOut(input, true);
  }

  /***
   * 
   * @param inputAssignment
   * @return
   */
  private SCHPullInOutInfo convertToGivenPullInOut(
      MtaUtsVehiclePullInPullOut inputAssignment, Boolean isPullIn) {

    SCHPullInOutInfo outputAssignment = new SCHPullInOutInfo();

    Long agencyId = mappingTool.getAgencyIdFromUtsAuthId(inputAssignment.getAuthId());

    // Get the pull in time and store it as it's used multiple times.
    DateTime pullInTime = getSchedDateAsCalByType(inputAssignment, true);

    // ditto for the pull out time.
    DateTime pullOutTime = getSchedDateAsCalByType(inputAssignment, false);

    // Set vehicle to new CPTVehicleIden
    CPTVehicleIden bus = new CPTVehicleIden();
    bus.setAgencyId(agencyId);
    bus.setVehicleId(inputAssignment.getBusNumber());
    bus.setDesignator(mappingTool.getVehicleDesignatorFromAgencyId(agencyId));
    outputAssignment.setVehicle(bus);

    // set time to be the scheduled pull in or pull out time, based on isPullIn
    DateTime adjMoveTime = isPullIn ? pullInTime : pullOutTime;
    DateTimeFormatter fmt = ISODateTimeFormat.dateTimeNoMillis();
    outputAssignment.setTime(fmt.print(adjMoveTime));

    // Set the value of pullIn equal to isPullIn
    outputAssignment.setPullIn(isPullIn);

    // Set the garage to a new CPTTransitFacilityIden representing a depot.
    CPTTransitFacilityIden depot = new CPTTransitFacilityIden();
    depot.setFacilityName(getMappedDepotId(inputAssignment));
    depot.setFacilityId(new Long(0));
    outputAssignment.setGarage(depot);

    // Set the date to be the service date.
    outputAssignment.setDate(inputAssignment.getDate());

    // Set the operator to a new CPTOperatorIden, using the agency id, pass
    // number and operator designator.
    CPTOperatorIden op = new CPTOperatorIden();
    op.setOperatorId(inputAssignment.getPassNumber());
    op.setAgencyId(agencyId);
    op.setDesignator(inputAssignment.getOperatorDesignator());
    outputAssignment.setOperator(op);

    // Set the block using a designator of a few items concatenated.
    SCHBlockIden block = new SCHBlockIden();
    block.setBlockId(new Long(0));

    DateTimeFormatter sDateDTF = DateTimeFormat.forPattern("MMddyyyy");
    String serviceDateMMDDYYYY = sDateDTF.print(inputAssignment.getServiceDate());

    DateTimeFormatter poTimeDTF = DateTimeFormat.forPattern("HHmm");
    String pullOutTimeHHMM = poTimeDTF.print(pullOutTime);

    String concatPODateTime = serviceDateMMDDYYYY + "_" + pullOutTimeHHMM;
    //Put the block designator information only if pull out is true
    if(!isPullIn) {
    	StringBuilder blockDesignator = new StringBuilder();
    	blockDesignator.append(getMappedDepotId(inputAssignment)); 
    	blockDesignator.append("_");
    	blockDesignator.append(inputAssignment.getRoute());
    	blockDesignator.append("_");
    	blockDesignator.append(inputAssignment.getRunNumberField());
    	blockDesignator.append("_");
    	blockDesignator.append(concatPODateTime);
    	block.setBlockDesignator(blockDesignator.toString());
    	//reset the string builder
    	blockDesignator.setLength(0);
    }
    outputAssignment.setBlock(block);

    // Set the local info, which holds the run-route.
    tcip_3_0_5_local.SCHPullInOutInfo localInfo = new tcip_3_0_5_local.SCHPullInOutInfo();
    StringBuilder runRoute = new StringBuilder();
    runRoute.append(inputAssignment.getRoute());
    runRoute.append("-");
    runRoute.append(inputAssignment.getRunNumberField());
    localInfo.setRunRoute(runRoute.toString());
    //reset the string builder
    runRoute.setLength(0);
    
    outputAssignment.setLocalSCHPullInOutInfo(localInfo);

    return outputAssignment;
  }
  
  private String getMappedDepotId(MtaUtsVehiclePullInPullOut utsPullout) {
    if (depotIdTranslator != null) {
      return depotIdTranslator.getMappedId(DATASOURCE_SYSTEM, utsPullout.getDepot());
    } else {
      return utsPullout.getDepot();
    }
  }

  private DateTime getSchedDateAsCalByType(
      MtaUtsVehiclePullInPullOut inputAssign, Boolean isPullIn) {
    DateTime result = null;

    String timeStr = isPullIn ? inputAssign.getSchedPI()
        : inputAssign.getSchedPO();

    result = mappingTool.calculatePullInOutDateFromDateUtsSuffixedTime(
        inputAssign.getServiceDate(), timeStr);

    return result;
  }

  public void setDepotIdTranslator(DepotIdTranslator depotIdTranslator) {
    this.depotIdTranslator = depotIdTranslator; 
  }
}
