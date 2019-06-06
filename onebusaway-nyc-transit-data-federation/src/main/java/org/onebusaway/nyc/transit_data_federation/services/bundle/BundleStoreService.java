package org.onebusaway.nyc.transit_data_federation.services.bundle;

import org.onebusaway.nyc.transit_data_federation.model.bundle.BundleItem;

import java.util.List;

/**
 * Sources of bundles--local or TDM-backed.
 * 
 * @author jmaki
 *
 */
public interface BundleStoreService {
  
  public List<BundleItem> getBundles() throws Exception;

  void deleteBundle(String bundleId) throws Exception;
  
}

