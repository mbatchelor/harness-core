package io.harness.cvng.core.services.api;

import io.harness.cvng.beans.CVHistogram;
import io.harness.cvng.beans.SplunkSampleResponse;
import io.harness.cvng.beans.SplunkSavedSearch;

import java.util.List;

public interface SplunkService {
  List<SplunkSavedSearch> getSavedSearches(String accountId, String connectorId, String requestGuid);

  CVHistogram getHistogram(String accountId, String connectorId, String query, String requestGuid);

  SplunkSampleResponse getSamples(String accountId, String connectorId, String query, String requestGuid);
}
