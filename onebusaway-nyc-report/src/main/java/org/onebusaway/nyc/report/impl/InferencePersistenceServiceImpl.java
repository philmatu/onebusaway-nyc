package org.onebusaway.nyc.report.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.onebusaway.nyc.report.model.ArchivedInferredLocationRecord;
import org.onebusaway.nyc.report.services.CcAndInferredLocationDao;
import org.onebusaway.nyc.report.services.CloudWatchService;
import org.onebusaway.nyc.report.services.InferencePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.amazonaws.services.cloudwatch.model.StandardUnit;

/**
* Manage the persistence of inference records. Handles the need to save in
* batches for performance, but with a timeout to keep the db up-to-date. Also
* does the post-processing of records via the TDS.
*
*/
public class InferencePersistenceServiceImpl implements
    InferencePersistenceService {
  private static Logger _log = LoggerFactory.getLogger(InferencePersistenceServiceImpl.class);
  private final int QUEUE_CAPACITY = 200000;

  private CcAndInferredLocationDao _locationDao;
  private CloudWatchService _cloudWatchService;
  
  private ScheduledExecutorService _executor;
  private int QUEUE_MONITOR_INITIAL_DELAY = 60;
  private int QUEUE_MONITOR_FREQUENCY = 60;

  @Autowired
  public void setLocationDao(CcAndInferredLocationDao locationDao) {
    this._locationDao = locationDao;
  }
  
  @Autowired
  public void setCloudWatchService(CloudWatchService cloudWatchService) {
    this._cloudWatchService = cloudWatchService;
  }

  private ArrayBlockingQueue<ArchivedInferredLocationRecord> messages = new ArrayBlockingQueue<ArchivedInferredLocationRecord>(
		  QUEUE_CAPACITY);

  private int _batchSize;

  public void setBatchSize(String batchSizeStr) {
    _batchSize = Integer.decode(batchSizeStr);
  }

  @Autowired
  private ThreadPoolTaskScheduler _taskScheduler;
  

  @PostConstruct
  public void setup() {
	_executor = Executors.newSingleThreadScheduledExecutor();
    final SaveThread saveThread = new SaveThread();
    final QueueMonitorThread queueMonitorThread = new QueueMonitorThread();
    _taskScheduler.scheduleWithFixedDelay(saveThread, 1000); // every second
    _executor.scheduleAtFixedRate(queueMonitorThread, QUEUE_MONITOR_INITIAL_DELAY, 
    		QUEUE_MONITOR_FREQUENCY, TimeUnit.SECONDS);
  }
  
  @PreDestroy
  public void stop() {
    _executor.shutdownNow();
  }

  @Override
  public void persist(ArchivedInferredLocationRecord record, String contents) {
      boolean accepted = messages.offer(record);
      if (!accepted) {
        _log.error("inf record " + record.getUUID() + " dropped, local buffer full! Clearing!");
        messages.clear();
      }
  }

  private class SaveThread implements Runnable {

    @Override
    public void run() {
      List<ArchivedInferredLocationRecord> reports = new ArrayList<ArchivedInferredLocationRecord>();
      // remove at most _batchSize (1000) records
      messages.drainTo(reports, _batchSize);
      logLatency(reports);
      try {
        _locationDao.saveOrUpdateRecords(reports.toArray(new ArchivedInferredLocationRecord[0]));

      } catch (Exception e) {
        _log.error("Error persisting=" + e);
      }
    }

    private void logLatency(List<ArchivedInferredLocationRecord> reports) {
      long now = System.currentTimeMillis();
      if (reports.size() > 0) {
        StringBuffer sb = new StringBuffer();
        sb.append("inf drained ");
        sb.append(reports.size());
        sb.append(" messages and has avg reported latency ");
        long reportedLatencySum = 0;
        for (int i = 0; i < reports.size(); i++) {
          reportedLatencySum += now - reports.get(i).getTimeReported().getTime();
        }
        sb.append((reportedLatencySum / reports.size()) / 1000);

        sb.append(" s and avg received latency ");
        long receivedLatencySum = 0;
        for (int i = 0; i < reports.size(); i++) {
          receivedLatencySum += now - reports.get(i).getLastUpdateTime();
        }
        sb.append((receivedLatencySum / reports.size()) / 1000);
        
        sb.append(" s and avg processing latency ");
        long processingLatencySum = 0;
        for (int i = 0; i < reports.size(); i++) {
          processingLatencySum += now - reports.get(i).getArchiveTimeReceived().getTime();
        }
        sb.append((processingLatencySum / reports.size()));
        sb.append(" ms");
        _log.info(sb.toString());
      } else {
        _log.info("inf nothing to do");
      }

    }
  }
  
  private class QueueMonitorThread implements Runnable {

	    @Override
	    public void run() {	    	
    	  int usedCapicitySize = QUEUE_CAPACITY - messages.remainingCapacity();
    	  double usedCapacityPct = (usedCapicitySize/QUEUE_CAPACITY) * 100;
    	  _cloudWatchService.publishMetric("UsedArchiveBufferCapacity", StandardUnit.Percent, usedCapacityPct);
    	  _log.info(messages.remainingCapacity() + " out of " + QUEUE_CAPACITY + " remaning capacity in archived record buffer.");  
	    }
  }

}