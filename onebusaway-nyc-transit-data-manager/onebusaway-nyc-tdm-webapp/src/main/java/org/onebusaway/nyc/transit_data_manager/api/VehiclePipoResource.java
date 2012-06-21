package org.onebusaway.nyc.transit_data_manager.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.onebusaway.nyc.transit_data_manager.adapters.ModelCounterpartConverter;
import org.onebusaway.nyc.transit_data_manager.adapters.api.processes.UtsPulloutsToDataCreator;
import org.onebusaway.nyc.transit_data_manager.adapters.data.ImporterVehiclePulloutData;
import org.onebusaway.nyc.transit_data_manager.adapters.input.VehicleAssignmentsOutputConverter;
import org.onebusaway.nyc.transit_data_manager.adapters.output.json.PullInOutFromTcip;
import org.onebusaway.nyc.transit_data_manager.adapters.output.model.json.PullInOut;
import org.onebusaway.nyc.transit_data_manager.adapters.output.model.json.VehiclePullInOutInfo;
import org.onebusaway.nyc.transit_data_manager.adapters.output.model.json.message.VehiclePipoMessage;
import org.onebusaway.nyc.transit_data_manager.adapters.tools.DepotIdTranslator;
import org.onebusaway.nyc.transit_data_manager.api.service.VehiclePullInOutService;
import org.onebusaway.nyc.transit_data_manager.api.sourceData.MostRecentFilePicker;
import org.onebusaway.nyc.transit_data_manager.api.sourceData.VehiclePipoUploadsFilePicker;
import org.onebusaway.nyc.transit_data_manager.json.JsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Web service resource to return vehicle pull in pull out data from the server. The data is parsed from a CSV
 * file in the configured directory and converted into TCIP format which is then sent back to the caller as 
 * JSON object.
 * @author abelsare
 *
 */
@Path("/pullouts")
@Component
@Scope("request")
public class VehiclePipoResource {

	private MostRecentFilePicker mostRecentFilePicker;
	private DepotIdTranslator depotIdTranslator;
	private JsonTool jsonTool;
	private VehicleAssignmentsOutputConverter converter;
	private VehiclePullInOutService vehiclePullInOutService;
	private ModelCounterpartConverter<VehiclePullInOutInfo, PullInOut> pulloutDataConverter;
	
	
	private static Logger log = LoggerFactory.getLogger(VehiclePipoResource.class);
	
	public VehiclePipoResource() throws IOException {
		mostRecentFilePicker = new VehiclePipoUploadsFilePicker(System.getProperty("tdm.vehiclepipoUploadDir"));
	    
	    try {
	      depotIdTranslator = new DepotIdTranslator(new File(System.getProperty("tdm.depotIdTranslationFile")));
	    } catch (IOException e) {
	      // Set depotIdTranslator to null and otherwise do nothing.
	      // Everything works fine without the depot id translator.
	      depotIdTranslator = null;
	    }
	}
	
	@Path("/list")
	@GET
	@Produces("application/json")
	public String getAllActivePullouts() {
		String method = "getAllActivePullouts";
		log.info("Starting " +method);

		ImporterVehiclePulloutData data = getVehiclePipoData();
		
		List<VehiclePullInOutInfo> activePullouts = vehiclePullInOutService.getActivePullOuts(data.getAllPullouts());

		VehiclePipoMessage message  = new VehiclePipoMessage();
		message.setPullouts(buildOutputData(activePullouts));
		message.setStatus("OK");
		
		String outputJson = serializeOutput(message, method);
		
		log.info(method + " returning json output.");
		
		return outputJson;
	}
	
	@Path("/{busNumber}/list")
	@GET
	@Produces("application/json")
	public String getCurrentlyActivePulloutsForBus(@PathParam("busNumber") String busNumber) {
		String method = "getCurrentlyActivePulloutsForBus";
		String output = null;
		
		log.info("Starting " +method);

		ImporterVehiclePulloutData data = getVehiclePipoData();
		
		Long busId = new Long(busNumber);
		
		List<VehiclePullInOutInfo> pulloutsByBus = data.getPulloutsByBus(busId);
		
		if(pulloutsByBus.isEmpty()) {
			output = "No pullouts found for bus : " +busId;
		} else {
			List<VehiclePullInOutInfo> currentActivePulloutByBus = getCurrentActivePullOutsByBus(pulloutsByBus);
			
			VehiclePipoMessage message  = new VehiclePipoMessage();
			message.setPullouts(buildOutputData(currentActivePulloutByBus));
			message.setStatus("OK");
			
			output = serializeOutput(message, method);
			
		}
		
		log.info(method + " returning json output.");
		return output;
	}
	
	@Path("/{depotName}/vehicles/list")
	@GET
	@Produces("application/json")
	public String getActivePulloutsForDepot(@PathParam("depotName") String depotName) {
		String method = "getCurrentlyActivePulloutsForDepot";
		String output = null;
		
		log.info("Starting " +method);

		ImporterVehiclePulloutData	data = getVehiclePipoData();
		
		List<VehiclePullInOutInfo> pulloutsByDepot = data.getPulloutsByDepot(depotName);
		
		if(pulloutsByDepot.isEmpty()) {
			output = "No pullouts found for depot : " +depotName;
		} else {
			//Get active pullouts once we have all pullouts for a depot
			List<VehiclePullInOutInfo> activePulloutsByDepot = vehiclePullInOutService.getActivePullOuts(pulloutsByDepot);
			
			VehiclePipoMessage message  = new VehiclePipoMessage();
			message.setPullouts(buildOutputData(activePulloutsByDepot));
			message.setStatus("OK");
			
			output = serializeOutput(message, method);
			
		}
		
		log.info(method + " returning json output.");
		return output;
	}
	
	@Path("/vehicles/{agencyId}/list")
	@GET
	@Produces("application/json")
	public String getActivePulloutsForAgency(@PathParam("agencyId") String agencyId) {
		String method = "getCurrentlyActivePulloutsForAgency";
		String output = null;
		
		log.info("Starting " +method);

		ImporterVehiclePulloutData	data = getVehiclePipoData();
		
		List<VehiclePullInOutInfo> pulloutsByAgency = data.getPulloutsByAgency(agencyId);
		
		if(pulloutsByAgency.isEmpty()) {
			output = "No pullouts found for agency : " +agencyId;
		} else {
			//Get active pullouts once we have all pullouts for a depot
			List<VehiclePullInOutInfo> activePulloutsByAgency = vehiclePullInOutService.getActivePullOuts(pulloutsByAgency);
			
			VehiclePipoMessage message  = new VehiclePipoMessage();
			message.setPullouts(buildOutputData(activePulloutsByAgency));
			message.setStatus("OK");
			
			output = serializeOutput(message, method);
			
		}
		
		log.info(method + " returning json output.");
		return output;
	}
	
	private List<VehiclePullInOutInfo> getCurrentActivePullOutsByBus(List<VehiclePullInOutInfo> pulloutsByBus) {
		List<VehiclePullInOutInfo> currentActivePulloutsByBus = new ArrayList<VehiclePullInOutInfo>();

		//Get active pullouts once we have all pullouts for a bus
		List<VehiclePullInOutInfo> activePulloutsByBus = vehiclePullInOutService.getActivePullOuts(pulloutsByBus);

		VehiclePullInOutInfo currentActivePulloutByBus = null;

		//Return pull out with latest pull out time if we have more than one active pull outs.
		if(activePulloutsByBus.size() > 1) {
			currentActivePulloutByBus = vehiclePullInOutService.getMostRecentActivePullout(activePulloutsByBus);
			currentActivePulloutsByBus.add(currentActivePulloutByBus);
		} else {
			if(!activePulloutsByBus.isEmpty()) {
				//this means there is only one pull out record present in the list.
				currentActivePulloutByBus = activePulloutsByBus.get(0);
				currentActivePulloutsByBus.add(currentActivePulloutByBus);
			}
		}
		
		return currentActivePulloutsByBus;
	}

	private String serializeOutput(VehiclePipoMessage message, String method) {
		String outputJson;
		StringWriter writer = null;
		try {
			writer = new StringWriter();
			jsonTool.writeJson(writer, message);
			outputJson = writer.toString();
			
		} catch(IOException e) {
		      log.info("Exception writing json output at VehiclePipoResource." +method);
		      log.debug(e.getMessage());
		      throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
		}
		finally {
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return outputJson;
	}
	
	private ImporterVehiclePulloutData getVehiclePipoData() {
		File inputFile = mostRecentFilePicker.getMostRecentSourceFile();
		UtsPulloutsToDataCreator creator;
		ImporterVehiclePulloutData pulloutData;
		
		 log.debug("Getting PulloutData object in getVehiclePipoData from " + inputFile.getPath());
		
		try {
			creator = new UtsPulloutsToDataCreator(inputFile);
			creator.setConverter(converter);
			
			if (depotIdTranslator == null) {
				log.info("Depot ID translation has not been enabled properly. Depot ids will not be translated.");
			} else {
				log.info("Using depot ID translation.");
			}
			creator.setDepotIdTranslator(depotIdTranslator);
			pulloutData = (ImporterVehiclePulloutData) creator.generateDataObject();
		} catch (FileNotFoundException e) {
			log.info("Could not create data object from " + inputFile.getPath());
		    log.info(e.getMessage());
		    throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
		}
		log.debug("Returning PulloutData object in getVehiclePipoData.");
		return pulloutData;
	}
	
	private List<PullInOut> buildOutputData(List<VehiclePullInOutInfo> vehiclePullInOuts) {
		List<PullInOut> outputData = new ArrayList<PullInOut>();
		//Convert to required JSON format
		for(VehiclePullInOutInfo vehiclePullInOut : vehiclePullInOuts) {
			outputData.add(pulloutDataConverter.convert(vehiclePullInOut));
		}
		return outputData;
	}

	/**
	 * Injects {@link JsonTool}
	 * @param jsonTool the jsonTool to set
	 */
	@Autowired
	public void setJsonTool(JsonTool jsonTool) {
		this.jsonTool = jsonTool;
	}

	/**
	 * Injects the converter
	 * @param converter the converter to set
	 */
	@Autowired
	public void setConverter(VehicleAssignmentsOutputConverter converter) {
		this.converter = converter;
	}

	/**
	 * Injects {@link VehiclePullInOutService}
	 * @param vehiclePullInOutService the vehiclePullInOutService to set
	 */
	@Autowired
	public void setVehiclePullInOutService(
			VehiclePullInOutService vehiclePullInOutService) {
		this.vehiclePullInOutService = vehiclePullInOutService;
	}

	/**
	 * Injects {@link PullInOutFromTcip}
	 * @param pulloutDataConverter the pulloutDataConverter to set
	 */
	@Autowired
	public void setPulloutDataConverter(
			ModelCounterpartConverter<VehiclePullInOutInfo, PullInOut> pulloutDataConverter) {
		this.pulloutDataConverter = pulloutDataConverter;
	}
}
