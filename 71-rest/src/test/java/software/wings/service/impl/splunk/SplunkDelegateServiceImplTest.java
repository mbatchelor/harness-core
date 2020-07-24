package software.wings.service.impl.splunk;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SRIRAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splunk.Job;
import com.splunk.JobArgs;
import com.splunk.JobCollection;
import com.splunk.SavedSearch;
import com.splunk.SavedSearchCollection;
import com.splunk.Service;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVHistogram;
import io.harness.cvng.beans.SplunkSampleResponse;
import io.harness.cvng.beans.SplunkSavedSearch;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import software.wings.WingsBaseTest;
import software.wings.beans.SplunkConfig;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.ThirdPartyApiCallLog;
import software.wings.service.impl.ThirdPartyApiCallLog.FieldType;
import software.wings.service.impl.ThirdPartyApiCallLog.ThirdPartyApiCallField;
import software.wings.service.impl.security.EncryptionServiceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class SplunkDelegateServiceImplTest extends WingsBaseTest {
  private SplunkConfig config;
  private String requestGuid;
  private String accountId;
  @Mock private DelegateLogService delegateLogService;
  private SplunkDelegateServiceImpl splunkDelegateService;
  @Before
  public void setUp() throws IllegalAccessException {
    config = SplunkConfig.builder()
                 .accountId("123")
                 .splunkUrl("https://input-prd-p-429h4vj2lsng.cloud.splunk.com:8089")
                 .username("123")
                 .password("123".toCharArray())
                 .build();
    requestGuid = generateUuid();
    splunkDelegateService = new SplunkDelegateServiceImpl();
    FieldUtils.writeField(splunkDelegateService, "delegateLogService", delegateLogService, true);
    splunkDelegateService = spy(splunkDelegateService);
    accountId = generateUuid();
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void initSplunkService() throws IllegalAccessException {
    FieldUtils.writeField(splunkDelegateService, "encryptionService", new EncryptionServiceImpl(null, null), true);
    splunkDelegateService.initSplunkService(config, Lists.emptyList());
    verify(splunkDelegateService, times(1)).initSplunkServiceWithToken(config);
    verify(splunkDelegateService, times(1)).initSplunkServiceWithBasicAuth(config);
  }

  @Test(expected = Exception.class)
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void initSplunkServiceOnlyToken() throws IllegalAccessException {
    when(splunkDelegateService.initSplunkServiceWithToken(config)).thenReturn(mock(Service.class));
    FieldUtils.writeField(splunkDelegateService, "encryptionService", new EncryptionServiceImpl(null, null), true);
    splunkDelegateService.initSplunkService(config, Lists.emptyList());
    verify(splunkDelegateService, times(1)).initSplunkServiceWithToken(config);
    verify(splunkDelegateService, times(1)).initSplunkServiceWithBasicAuth(config);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSavedSearches_withCorrectResults() {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any(), anyList());
    SplunkConfig splunkConfig = SplunkConfig.builder().accountId(accountId).splunkUrl("splunk-url/").build();
    SavedSearchCollection savedSearchCollection = mock(SavedSearchCollection.class);
    SavedSearch savedSearch = mock(SavedSearch.class);
    when(savedSearch.getSearch()).thenReturn("search query");
    when(savedSearch.getTitle()).thenReturn("search query title");
    when(savedSearchCollection.values()).thenReturn(Lists.newArrayList(savedSearch));
    when(service.getSavedSearches(any(JobArgs.class))).thenReturn(savedSearchCollection);
    List<SplunkSavedSearch> splunkSavedSearches =
        splunkDelegateService.getSavedSearches(splunkConfig, new ArrayList<>(), requestGuid);
    assertThat(splunkSavedSearches).hasSize(1);
    assertThat(splunkSavedSearches.get(0).getSearchQuery()).isEqualTo("search query");
    assertThat(splunkSavedSearches.get(0).getTitle()).isEqualTo("search query title");
    ThirdPartyApiCallLog apiCallLog = captureThirdPartyAPICallLog();
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetch request to splunk-url/");
    assertThat(apiCallLog.getStateExecutionId()).isEqualTo(requestGuid);
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Url")
                      .type(FieldType.URL)
                      .value("splunk-url/saved/searches")
                      .build());
    assertThat(apiCallLog.getRequest())
        .contains(
            ThirdPartyApiCallField.builder().name("Query").type(FieldType.JSON).value("{\"app\":\"search\"}").build());
    assertThat(apiCallLog.getResponse())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Response Body")
                      .type(FieldType.JSON)
                      .value("[{\"title\":\"search query title\",\"searchQuery\":\"search query\"}]")
                      .build());
    assertThat(apiCallLog.getResponse()).hasSize(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSavedSearches_withException() {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any(), anyList());
    SplunkConfig splunkConfig = SplunkConfig.builder().accountId(accountId).splunkUrl("splunk-url").build();
    SavedSearchCollection savedSearchCollection = mock(SavedSearchCollection.class);
    SavedSearch savedSearch = mock(SavedSearch.class);
    when(savedSearch.getSearch()).thenReturn("search query");
    when(savedSearch.getTitle()).thenReturn("search query title");
    when(savedSearchCollection.values()).thenReturn(Lists.newArrayList(savedSearch));
    when(service.getSavedSearches(any(JobArgs.class))).thenThrow(new RuntimeException("from test"));
    assertThatThrownBy(() -> splunkDelegateService.getSavedSearches(splunkConfig, new ArrayList<>(), requestGuid))
        .hasMessage("from test");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetHistogram() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any(), anyList());
    SplunkConfig splunkConfig = SplunkConfig.builder().accountId(accountId).splunkUrl("splunk-url").build();
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_histogram_query.json"));
    CVHistogram histogram =
        splunkDelegateService.getHistogram(splunkConfig, new ArrayList<>(), "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | timechart count span=6h | table _time, count"), any());
    assertThat(histogram.getQuery()).isEqualTo("exception");
    assertThat(histogram.getBars()).hasSize(29);
    assertThat(histogram.getBars().get(0))
        .isEqualTo(CVHistogram.Bar.builder().count(10).timestamp(1589889600000L).build());
    ThirdPartyApiCallLog apiCallLog = captureThirdPartyAPICallLog();
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetch request to splunk-url");
    assertThat(apiCallLog.getStateExecutionId()).isEqualTo(requestGuid);
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder().name("Url").type(FieldType.URL).value("splunk-url").build());
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Query")
                      .type(FieldType.TEXT)
                      .value("search exception | timechart count span=6h | table _time, count")
                      .build());
    assertThat(apiCallLog.getResponse()).hasSize(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSamples() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any(), anyList());
    SplunkConfig splunkConfig = SplunkConfig.builder().accountId(accountId).splunkUrl("splunk-url").build();
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_samples_query.json"));
    SplunkSampleResponse samples =
        splunkDelegateService.getSamples(splunkConfig, new ArrayList<>(), "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | head 10"), any());
    assertThat(samples.getRawSampleLogs()).hasSize(10);
    assertThat(samples.getRawSampleLogs().get(0))
        .isEqualTo(
            "2020-05-26 18:10:39,278 [GitChangeSet] INFO  software.wings.yaml.gitSync.GitChangeSetRunnable - Not continuing with GitChangeSetRunnable job");
    assertThat(samples.getSample())
        .isEqualTo(JsonUtils.asObject("{\n"
                + "            \"linecount\": \"1\",\n"
                + "            \"splunk_server\": \"splunk-dev\",\n"
                + "            \"host\": \"splunk-dev\",\n"
                + "            \"index\": \"main\",\n"
                + "            \"sourcetype\": \"delegate-logs-local\",\n"
                + "            \"source\": \"delegate.2020-05-21.log\"\n"
                + "        }",
            HashMap.class));
    ThirdPartyApiCallLog apiCallLog = captureThirdPartyAPICallLog();
    assertThat(apiCallLog.getTitle()).isEqualTo("Fetch request to splunk-url");
    assertThat(apiCallLog.getStateExecutionId()).isEqualTo(requestGuid);
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder().name("Url").type(FieldType.URL).value("splunk-url").build());
    assertThat(apiCallLog.getRequest())
        .contains(ThirdPartyApiCallField.builder()
                      .name("Query")
                      .type(FieldType.TEXT)
                      .value("search exception | head 10")
                      .build());
    assertThat(apiCallLog.getResponse()).hasSize(2);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSamples_withoutRawLogs() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any(), anyList());
    SplunkConfig splunkConfig = SplunkConfig.builder().accountId(accountId).splunkUrl("splunk-url").build();
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any()))
        .thenReturn(getSplunkJsonResponseInputStream("splunk_json_response_for_samples_invalid_query.json"));
    SplunkSampleResponse samples = splunkDelegateService.getSamples(splunkConfig, new ArrayList<>(),
        "index=_internal source=\"*metrics.log\" eps \"group=per_source_thruput\" NOT filetracker | eval events=eps*kb/kbps | timechart fixedrange=t span=1m limit=5 sum(events) by series",
        requestGuid);
    verify(jobCollection)
        .create(
            eq("search index=_internal source=\"*metrics.log\" eps \"group=per_source_thruput\" NOT filetracker | eval events=eps*kb/kbps | timechart fixedrange=t span=1m limit=5 sum(events) by series | head 10"),
            any());
    assertThat(samples.getRawSampleLogs()).hasSize(0);
    assertThat(samples.getSample())
        .isEqualTo(JsonUtils.asObject("{\n"
                + "  \"/opt/splunk/var/log/introspection/resource_usage.log\": \"27.0000000\",\n"
                + "  \"/opt/splunk/var/log/splunk/health.log\": \"6.00000000\",\n"
                + "  \"/opt/splunk/var/log/splunk/metrics.log\": \"80.00000000\",\n"
                + "  \"OTHER\": \"15.0000000000\",\n"
                + "  \"http:httpcollector\": \"9.00000000000\"\n"
                + "}",
            HashMap.class));
    assertThat(samples.getErrorMessage())
        .isEqualTo("Wrong kind of splunk query. Query should have raw(_raw field) message. Please check the query");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetSample_withEmptyResults() throws IOException {
    Service service = mock(Service.class);
    doReturn(service).when(splunkDelegateService).initSplunkService(any(), anyList());
    SplunkConfig splunkConfig = mock(SplunkConfig.class);
    JobCollection jobCollection = mock(JobCollection.class);
    when(service.getJobs()).thenReturn(jobCollection);
    Job job = mock(Job.class);
    when(jobCollection.create(any(), any())).thenReturn(job);
    when(job.getResults(any())).thenReturn(getSplunkJsonResponseInputStream("splunk_json_search_response_empty.json"));
    SplunkSampleResponse samples =
        splunkDelegateService.getSamples(splunkConfig, new ArrayList<>(), "exception", requestGuid);
    verify(jobCollection).create(eq("search exception | head 10"), any());
    assertThat(samples.getRawSampleLogs()).isEmpty();
    assertThat(samples.getSample()).isEmpty();
  }

  private ThirdPartyApiCallLog captureThirdPartyAPICallLog() {
    ArgumentCaptor<ThirdPartyApiCallLog> captor = ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(delegateLogService, times(1)).save(eq(accountId), captor.capture());
    return captor.getValue();
  }

  private InputStream getSplunkJsonResponseInputStream(String jsonFile) throws IOException {
    String splunkResponse =
        IOUtils.toString(SplunkDelegateServiceImpl.class.getResourceAsStream(jsonFile), StandardCharsets.UTF_8.name());
    return IOUtils.toInputStream(splunkResponse, Charset.defaultCharset());
  }
}
