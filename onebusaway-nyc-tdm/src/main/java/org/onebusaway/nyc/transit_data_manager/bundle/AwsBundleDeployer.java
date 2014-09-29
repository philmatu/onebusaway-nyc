package org.onebusaway.nyc.transit_data_manager.bundle;


import org.onebusaway.nyc.transit_data_manager.bundle.model.BundleStatus;

import java.util.List;

public interface AwsBundleDeployer {
  void setup();
  String get(String s3Key, String destinationDirectory);
  void setUser(String user);
  void setPassword(String password);
  void setBucketName(String bucketName);
  List<String> listFiles(String directory, int maxResults);
  void deploy(BundleStatus status, String s3Path);
  List<String> listBundlesForServing(String s3Path);
}
