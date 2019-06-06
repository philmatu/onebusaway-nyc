package org.onebusaway.nyc.report_archive.impl;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.onebusaway.nyc.report.impl.CcAndInferredLocationFilter;
import org.onebusaway.nyc.report.impl.CcLocationCache;
import org.onebusaway.nyc.report.model.ArchivedInferredLocationRecord;
import org.onebusaway.nyc.report.model.CcAndInferredLocationRecord;
import org.onebusaway.nyc.report.model.CcLocationReportRecord;
import org.onebusaway.nyc.report.model.InvalidLocationRecord;
import org.onebusaway.nyc.report.services.CcAndInferredLocationDao;
import org.onebusaway.nyc.report.services.RecordValidationService;
import org.onebusaway.nyc.report.util.HQLBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class ArchiveCcAndInferredLocationDaoImpl implements CcAndInferredLocationDao {

	protected static Logger _log = LoggerFactory.getLogger(ArchiveCcAndInferredLocationDaoImpl.class);

	private SessionFactory _sessionFactory;
	
	private static final String SPACE = " ";

	@Autowired
	private CcLocationCache _ccLocationCache;
	
	private RecordValidationService validationService;
	
	@Autowired
	public void setValidationService(RecordValidationService validationService) {
		this.validationService = validationService;
	}

	public void setCcLocationCache(CcLocationCache cache) {
		_ccLocationCache = cache;
	}

	@Autowired
	@Qualifier("sessionFactory")
	public void setSessionFactory(SessionFactory sessionFactory) {
		_sessionFactory = sessionFactory;
	}

	public Session getSession() {
		return _sessionFactory.getCurrentSession();
	}

	@Transactional(value="transactionManager", rollbackFor = Throwable.class)
	@Override
	public void saveOrUpdateRecord(ArchivedInferredLocationRecord record) {
		getSession().saveOrUpdate(record);
		
		CcLocationReportRecord cc = findRealtimeRecord(record);
		if (cc != null) {
			CcAndInferredLocationRecord lastKnown = new CcAndInferredLocationRecord(
					record, cc);
			getSession().saveOrUpdate(lastKnown);
		}

		getSession().flush();
		getSession().clear();
	}

	@Transactional(value="transactionManager", rollbackFor = Throwable.class)
	@Override
	public void saveOrUpdateRecords(ArchivedInferredLocationRecord... records) {
		List<ArchivedInferredLocationRecord> ArchivedInferredLocationRecord = getInferredLocationRecords(records);
		Collection<CcAndInferredLocationRecord> lastKnownRecords = getLastKnownRecords(records);

		for(ArchivedInferredLocationRecord ilr : ArchivedInferredLocationRecord){
			getSession().saveOrUpdate(ilr);
		}

		for(CcAndInferredLocationRecord lkr : lastKnownRecords){
			getSession().saveOrUpdate(lkr);
		}

		getSession().flush();
		getSession().clear();
	}
	
	@Transactional(value="transactionManager", rollbackFor = Throwable.class)
	@Override
	public void saveOrUpdateRecord(CcAndInferredLocationRecord record) {
		getSession().saveOrUpdate(record);
		getSession().flush();
		getSession().clear();
	}
	
	@Transactional(value="transactionManager", rollbackFor = Throwable.class)
	@Override
	public void saveOrUpdateRecords(Collection<CcAndInferredLocationRecord> records) {		
		for(CcAndInferredLocationRecord record : records){
			getSession().saveOrUpdate(record);
		}
		getSession().flush();
		getSession().clear();
	}
	
	protected List<ArchivedInferredLocationRecord> getInferredLocationRecords(ArchivedInferredLocationRecord[] records){
		return Arrays.asList(records);
	}
	
	protected Collection<CcAndInferredLocationRecord> getLastKnownRecords(ArchivedInferredLocationRecord[] records){
		LinkedHashMap<Integer, CcAndInferredLocationRecord> lastKnownRecords = new LinkedHashMap<Integer, CcAndInferredLocationRecord>(
				records.length);
		for (ArchivedInferredLocationRecord record : records) {
			CcLocationReportRecord cc = findRealtimeRecord(record);
			if (cc != null) {
				CcAndInferredLocationRecord lastKnown = new CcAndInferredLocationRecord(record, cc);
				if (validationService.validateLastKnownRecord(lastKnown)) {
				  lastKnownRecords.put(lastKnown.getVehicleId(), lastKnown);
				} else {
				  discardRecord(lastKnown);
				}
			}
		}
		
		return lastKnownRecords.values();
	}
	

	protected CcLocationReportRecord findRealtimeRecord(
			ArchivedInferredLocationRecord record) {
		// first check cache for realtime record
		CcLocationReportRecord realtime = _ccLocationCache.get(record.getUUID());

		// if not in cache log cache miss
		if (realtime == null) {
			/*
			 * NOTE: db is NOT queried for lost record for
			 * performance reasons.  Assume queue has fallen
			 * behind and incoming update will correct this.
			 */
			_log.info("cache miss for " + record.getVehicleId());
		}
		return realtime;
	}

	@Transactional(value="transactionManager", rollbackFor = Throwable.class)
	@Override
	public List<CcAndInferredLocationRecord> getAllLastKnownRecords(
			final Map<CcAndInferredLocationFilter, String> filter) {

		HQLBuilder queryBuilder = new HQLBuilder();
		StringBuilder hql = queryBuilder.from(new StringBuilder(), "CcAndInferredLocationRecord");
		
		hql = addQueryParam(queryBuilder, hql, CcAndInferredLocationFilter.DEPOT_ID, 
				filter.get(CcAndInferredLocationFilter.DEPOT_ID));
		hql = addQueryParam(queryBuilder, hql, CcAndInferredLocationFilter.INFERRED_ROUTEID, 
				filter.get(CcAndInferredLocationFilter.INFERRED_ROUTEID));
		hql = addQueryParam(queryBuilder, hql, CcAndInferredLocationFilter.INFERRED_PHASE, 
				filter.get(CcAndInferredLocationFilter.INFERRED_PHASE));

		String boundingBox = filter.get(CcAndInferredLocationFilter.BOUNDING_BOX);
		if(StringUtils.isNotBlank(boundingBox)) {
			hql = addBoundingBoxParam(hql);
		}
		
		hql = queryBuilder.order(hql, "vehicleId", null);

		final StringBuilder hqlQuery = hql;

		Query query = buildQuery(filter, hqlQuery);

		_log.debug("Executing query : " + hqlQuery.toString());

		return query.list();
	}

	@Transactional(value="transactionManager", rollbackFor = Throwable.class)
	@Override
	public CcAndInferredLocationRecord getLastKnownRecordForVehicle(
			Integer vehicleId) throws Exception {

		if (vehicleId == null) {
			return null;
		}
		return (CcAndInferredLocationRecord) getSession().get(CcAndInferredLocationRecord.class, vehicleId);
	}
	
	
	private Query buildQuery(Map<CcAndInferredLocationFilter, String> filter, StringBuilder hqlQuery) {
		Query query = getSession().createQuery(hqlQuery.toString());
		
		setNamedParamter(query, CcAndInferredLocationFilter.DEPOT_ID, 
				filter.get(CcAndInferredLocationFilter.DEPOT_ID));
		setNamedParamter(query, CcAndInferredLocationFilter.INFERRED_ROUTEID, 
				filter.get(CcAndInferredLocationFilter.INFERRED_ROUTEID));
		setNamedParamter(query, CcAndInferredLocationFilter.INFERRED_PHASE, 
				filter.get(CcAndInferredLocationFilter.INFERRED_PHASE));
		setBoundingBoxParameter(query, filter.get(CcAndInferredLocationFilter.BOUNDING_BOX));
		
		return query;
	}
	
	protected StringBuilder addQueryParam(HQLBuilder queryBuilder, StringBuilder hql, CcAndInferredLocationFilter param, String field) {
		if(StringUtils.isNotBlank(field)) {
			hql = queryBuilder.where(hql, param.getValue(), ":" +param.getValue());
		}
		return hql;
	}

	protected StringBuilder addBoundingBoxParam(StringBuilder hql) {
		if(hql.toString().contains("where")) {
			hql.append("and(" + buildCoordinatesQueryString() +")");
		} else {
			hql.append("where(" + buildCoordinatesQueryString() +")");
		}
		hql.append(SPACE);
		
		return hql;
	}

	protected void setNamedParamter(Query query, CcAndInferredLocationFilter param, String value) {
		if(StringUtils.isNotBlank(value)) {
			query.setParameter(param.getValue(), value);
		}
	}
	
	protected void setBoundingBoxParameter(Query query, String boundingBox) {
		if(StringUtils.isNotBlank(boundingBox)) {
			String [] coordinates = boundingBox.split(",");
			BigDecimal minLongitude = new BigDecimal(coordinates[0]);
			BigDecimal minLatitude = new BigDecimal(coordinates[1]);
			BigDecimal maxLongitude = new BigDecimal(coordinates[2]);
			BigDecimal maxLatitude = new BigDecimal(coordinates[3]);
			
			query.setParameter("minLongitude", minLongitude);
			query.setParameter("minLatitude", minLatitude);
			query.setParameter("maxLongitude", maxLongitude);
			query.setParameter("maxLatitude", maxLatitude);
		}
		
	}
	

	protected String buildCoordinatesQueryString() {
		
		StringBuilder query = new StringBuilder("(latitude >=").append(SPACE);
		query.append(":minLatitude").append(SPACE);
		query.append("and").append(SPACE);
		query.append("latitude <").append(SPACE);
		query.append(":maxLatitude").append(SPACE);
		query.append("and").append(SPACE);
		query.append("longitude >=").append(SPACE);
		query.append(":minLongitude").append(SPACE);
		query.append("and").append(SPACE);
		query.append("longitude <").append(SPACE);
		query.append(":maxLongitude").append(")").append(SPACE);
		query.append("or").append(SPACE);
		query.append("(inferredLatitude >=").append(SPACE);
		query.append(":minLatitude").append(SPACE);
		query.append("and").append(SPACE);
		query.append("inferredLatitude <");
		query.append(":maxLatitude").append(SPACE);
		query.append("and").append(SPACE);
		query.append("inferredLongitude >=").append(SPACE);
		query.append(":minLongitude").append(SPACE);
		query.append("and").append(SPACE);
		query.append("inferredLongitude <").append(SPACE);
		query.append(":maxLongitude").append(")");
		
		return query.toString();
	}

	protected void discardRecord(CcAndInferredLocationRecord record) {
    _log.error(
        "Discarding inferred record for vehicle : {} as inferred latitude or inferred longitude "
            + "values are out of range, or tripID/blockID is too long", record.getVehicleId());
	}

	@Override
	@Transactional(rollbackFor = Throwable.class, propagation = Propagation.REQUIRES_NEW)
	public void handleException(String content, Throwable error,
			Date timeReceived) {
		InvalidLocationRecord ilr = new InvalidLocationRecord(content, error,
				timeReceived);
		getSession().saveOrUpdate(ilr);
		// clear from level one cache
		getSession().flush();
		getSession().evict(ilr);
	}

	@Override
	public Integer getArchiveInferredLocationCount() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer getCcLocationReportRecordCount() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer getCcAndInferredLocationCount() {
		// TODO Auto-generated method stub
		return null;
	}
}