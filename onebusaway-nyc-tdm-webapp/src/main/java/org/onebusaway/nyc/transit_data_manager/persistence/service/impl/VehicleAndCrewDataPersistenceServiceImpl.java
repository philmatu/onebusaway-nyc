package org.onebusaway.nyc.transit_data_manager.persistence.service.impl;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.log4j.Level;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.onebusaway.nyc.transit_data_manager.adapters.data.ImporterVehiclePulloutData;
import org.onebusaway.nyc.transit_data_manager.adapters.data.OperatorAssignmentData;
import org.onebusaway.nyc.transit_data_manager.adapters.output.model.json.OperatorAssignment;
import org.onebusaway.nyc.transit_data_manager.adapters.output.model.json.PullInOut;
import org.onebusaway.nyc.transit_data_manager.adapters.tools.DepotIdTranslator;
import org.onebusaway.nyc.transit_data_manager.api.service.CrewAssignmentDataProviderService;
import org.onebusaway.nyc.transit_data_manager.api.vehiclepipo.service.VehiclePullInOutDataProviderService;
import org.onebusaway.nyc.transit_data_manager.persistence.model.CrewAssignmentRecord;
import org.onebusaway.nyc.transit_data_manager.persistence.model.VehiclePipoRecord;
import org.onebusaway.nyc.transit_data_manager.persistence.service.VehicleAndCrewDataPersistenceService;
import org.onebusaway.nyc.util.logging.LoggingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;


/**
 * Default implementation of {@link VehicleAndCrewDataPersistenceService}
 * @author abelsare
 *
 */
public class VehicleAndCrewDataPersistenceServiceImpl implements VehicleAndCrewDataPersistenceService{

	private VehiclePullInOutDataProviderService vehiclePullInOutDataProviderService;
	private CrewAssignmentDataProviderService crewAssignmentDataProviderService;
	private DepotIdTranslator depotIdTranslator;
	private LoggingService loggingService;
	private SessionFactory _sessionFactory;
	
	private static final Logger log = LoggerFactory.getLogger(VehicleAndCrewDataPersistenceServiceImpl.class);
	
	public VehicleAndCrewDataPersistenceServiceImpl() {
		try {
			depotIdTranslator = new DepotIdTranslator(new File(System.getProperty("tdm.depotIdTranslationFile")));
		} catch (IOException e) {
			// Set depotIdTranslator to null and otherwise do nothing.
			// Everything works fine without the depot id translator.
			depotIdTranslator = null;
		}
	}
	
	@Override
	@Transactional("transactionManagerArchive")
	public void saveVehiclePulloutData() throws DataAccessResourceFailureException {
		List<VehiclePipoRecord> vehicleRecords = new ArrayList<VehiclePipoRecord>();
		
		//Get the data
		ImporterVehiclePulloutData pulloutData = vehiclePullInOutDataProviderService.
																	getVehiclePipoData(depotIdTranslator);
		
		List<PullInOut> pullouts = vehiclePullInOutDataProviderService.
											buildResponseData(pulloutData.getAllPullouts());
		
		//build vehicle record model objects
		for(PullInOut pullout : pullouts) {
			vehicleRecords.add(buildVehiclePipoRecord(pullout));
		}
		
		log.info("Persisting {} vehicle pullout records", vehicleRecords.size());

		try {
			Session session = getSession();
			for(Iterator<VehiclePipoRecord> it = vehicleRecords.iterator(); it.hasNext();){
				session.saveOrUpdate(it.next());
			}
			String message = "Persisted " + vehicleRecords.size() + " vehicle pullout records";
			log(message);
		} catch(DataAccessException e) {
			log.error("Error persisting pullout records");
			e.printStackTrace();
		}
		
	}

	@Override
	@Transactional("transactionManagerArchive")
	public void saveCrewAssignmentData() throws DataAccessResourceFailureException {
		List<CrewAssignmentRecord> crewAssignments = new ArrayList<CrewAssignmentRecord>();
		
		//Get the data
		OperatorAssignmentData operatorAssignmentData = crewAssignmentDataProviderService.
				getCrewAssignmentData(depotIdTranslator);
		
		DateMidnight serviceDate;
		DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.date();
		
		DateTime now = dateTimeFormatter.parseDateTime(
				new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
		
		try {
			//service date is the day before now as the job executes at 4 am and we are interested in
			//yesterday's data
			serviceDate = new DateMidnight(now.minusDays(1));
		} catch (IllegalArgumentException e) {
			log.debug(e.getMessage());
			throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
		}
		
		List<OperatorAssignment> operatorAssignments = crewAssignmentDataProviderService.buildResponseData(
				operatorAssignmentData.getOperatorAssignmentsByServiceDate(serviceDate));
		
		//build crew assignment model object
		for(OperatorAssignment operatorAssignment : operatorAssignments) {
			crewAssignments.add(buildCrewAssignmentRecord(operatorAssignment));
		}
		
		log.info("Persisting {} crew assignment records", crewAssignments.size());

		try {
			Session session = getSession();
			for(Iterator<CrewAssignmentRecord> it = crewAssignments.iterator(); it.hasNext();){
				session.saveOrUpdate(it.next());
			}
			String message = "Persisted " + crewAssignments.size() + " crew assignment records";
			log(message);
		} catch(DataAccessException e) {
			log.error("Error persisting crew assignment records");
			e.printStackTrace();
		}
	}
	
	private void log(String message) {
		String component = System.getProperty("tdm.chefRole");
		loggingService.log(component, Level.INFO, message);
	}
	
	private VehiclePipoRecord buildVehiclePipoRecord(PullInOut pullout) {
		VehiclePipoRecord vehicleRecord = new VehiclePipoRecord();
		
		vehicleRecord.setVehicleId(Integer.valueOf(pullout.getVehicleId()));
		vehicleRecord.setAgencyId(pullout.getAgencyId());
		vehicleRecord.setAgencyIdTcip(Integer.valueOf(pullout.getAgencyIdTcip()));
		vehicleRecord.setDepotId(pullout.getDepot());
		vehicleRecord.setOperatorId(pullout.getOperatorId());
		vehicleRecord.setRun(pullout.getRun());
		vehicleRecord.setServiceDate(getServiceDate(pullout.getServiceDate(), "VehiclePipoRecord"));
		vehicleRecord.setPullinTime(getDateTime(pullout.getPullinTime()));
		vehicleRecord.setPulloutTime(getDateTime(pullout.getPulloutTime()));
		
		return vehicleRecord;
	}
	
	private CrewAssignmentRecord buildCrewAssignmentRecord(OperatorAssignment operatorAssignment) {
		CrewAssignmentRecord crewAssignmentRecord = new CrewAssignmentRecord();
		
		crewAssignmentRecord.setAgencyId(operatorAssignment.getAgencyId());
		crewAssignmentRecord.setDepotId(operatorAssignment.getDepot());
		crewAssignmentRecord.setOperatorId(operatorAssignment.getPassId());
		crewAssignmentRecord.setRun(operatorAssignment.getRunRoute() + "-" + operatorAssignment.getRunNumber());
		crewAssignmentRecord.setServiceDate(getServiceDate(operatorAssignment.getServiceDate(), "CrewAssignmentRecord"));
		crewAssignmentRecord.setUpdated(getDateTime(operatorAssignment.getUpdated()));
		
		return crewAssignmentRecord;
	}
	
	private Date getServiceDate(String serviceDateString, String recordType) {
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		Date serviceDate = null;
		try {
			serviceDate = formatter.parse(serviceDateString);
		} catch (ParseException e) {
			log.error("Error parsing service date for {}. The expected date format is " +
					"yyyy-MM-dd and the given date is {}", recordType, serviceDateString);
			e.printStackTrace();
		}
		return serviceDate;
	}
	
	private Date getDateTime(String dateTimeString) {
		DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();
		return formatter.parseDateTime(dateTimeString).toDate();
		
	}
	
	/**
	 * @param vehiclePullInOutDataProviderService the vehiclePullInOutDataProviderService to set
	 */
	@Autowired
	public void setVehiclePullInOutDataProviderService(
			VehiclePullInOutDataProviderService vehiclePullInOutDataProviderService) {
		this.vehiclePullInOutDataProviderService = vehiclePullInOutDataProviderService;
	}

	/**
	 * @param crewAssignmentDataProviderService the crewAssignmentDataProviderService to set
	 */
	@Autowired
	public void setCrewAssignmentDataProviderService(
			CrewAssignmentDataProviderService crewAssignmentDataProviderService) {
		this.crewAssignmentDataProviderService = crewAssignmentDataProviderService;
	}
	
	/**
	 * @param sessionFactory the sessionFactory to set
	 */
	@Autowired
	@Qualifier("archiveSessionFactory")
	public void setSessionFactory(SessionFactory sessionFactory) {
		_sessionFactory = sessionFactory;
	}

	private Session getSession(){
		return _sessionFactory.getCurrentSession();
	}


	/**
	 * @param loggingService the loggingService to set
	 */
	@Autowired
	public void setLoggingService(LoggingService loggingService) {
		this.loggingService = loggingService;
	}

}
