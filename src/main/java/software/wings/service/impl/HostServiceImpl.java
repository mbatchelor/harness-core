package software.wings.service.impl;

import static org.mongodb.morphia.mapping.Mapper.ID_KEY;
import static software.wings.beans.Host.HostBuilder.aHost;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import software.wings.beans.Host;
import software.wings.beans.Host.HostBuilder;
import software.wings.beans.Infra;
import software.wings.beans.Tag;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.TagService;
import software.wings.utils.BoundedInputStream;
import software.wings.utils.HostCsvFileHelper;

import java.io.File;
import java.util.List;
import javax.inject.Inject;
import javax.validation.executable.ValidateOnExecution;

// TODO: Auto-generated Javadoc

/**
 * Created by anubhaw on 5/9/16.
 */
@ValidateOnExecution
public class HostServiceImpl implements HostService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private HostCsvFileHelper csvFileHelper;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private TagService tagService;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#list(software.wings.dl.PageRequest)
   */
  @Override
  public PageResponse<Host> list(PageRequest<Host> req) {
    return wingsPersistence.query(Host.class, req);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#get(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public Host get(String appId, String infraId, String hostId) {
    return wingsPersistence.createQuery(Host.class)
        .field(ID_KEY)
        .equal(hostId)
        .field("infraId")
        .equal(infraId)
        .field("appId")
        .equal(appId)
        .get();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#save(software.wings.beans.Host)
   */
  @Override
  public Host save(Host host) {
    host.setHostName(host.getHostName().trim());
    return wingsPersistence.saveAndGet(Host.class, host);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#update(software.wings.beans.Host)
   */
  @Override
  public Host update(Host host) {
    Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                                          .put("hostName", host.getHostName())
                                          .put("hostConnAttr", host.getHostConnAttr());
    if (host.getBastionConnAttr() != null) {
      builder.put("bastionConnAttr", host.getBastionConnAttr());
    }
    if (host.getTags() != null) {
      builder.put("tags", host.getTags());
    }
    wingsPersistence.updateFields(Host.class, host.getUuid(), builder.build());
    return wingsPersistence.saveAndGet(Host.class, host);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#importHosts(java.lang.String, java.lang.String,
   * software.wings.utils.BoundedInputStream)
   */
  @Override
  public int importHosts(String appId, String infraId, BoundedInputStream inputStream) {
    Infra infra = wingsPersistence.get(Infra.class, infraId); // TODO: validate infra
    List<Host> hosts = csvFileHelper.parseHosts(infra, inputStream);
    List<String> Ids = wingsPersistence.save(hosts);
    return Ids.size();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#getHostsById(java.lang.String, java.util.List)
   */
  @Override
  public List<Host> getHostsById(String appId, List<String> hostUuids) {
    return wingsPersistence.createQuery(Host.class)
        .field("appId")
        .equal(appId)
        .field(ID_KEY)
        .hasAnyOf(hostUuids)
        .asList();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#getHostsByTags(java.lang.String, java.util.List)
   */
  @Override
  public List<Host> getHostsByTags(String appId, List<Tag> tags) {
    return wingsPersistence.createQuery(Host.class).field("appId").equal(appId).field("tags").hasAnyOf(tags).asList();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#exportHosts(java.lang.String, java.lang.String)
   */
  @Override
  public File exportHosts(String appId, String infraId) {
    List<Host> hosts = wingsPersistence.createQuery(Host.class).field("infraId").equal(infraId).asList();
    return csvFileHelper.createHostsFile(hosts);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#getInfraId(java.lang.String, java.lang.String)
   */
  @Override
  public String getInfraId(String envId, String appId) {
    return wingsPersistence.createQuery(Infra.class).field("envId").equal(envId).get().getUuid();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#delete(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public void delete(String appId, String infraId, String hostId) {
    Host host = get(appId, infraId, hostId);
    if (host != null) {
      wingsPersistence.delete(host);
      serviceTemplateService.deleteHostFromTemplates(host);
      tagService.deleteHostFromTags(host);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.HostService#bulkSave(software.wings.beans.Host, java.util.List)
   */
  @Override
  public void bulkSave(Host baseHost, List<String> hostNames) {
    hostNames.forEach(hostName -> {
      if (hostName != null && hostName.length() > 0) {
        HostBuilder builder = aHost()
                                  .withHostName(hostName)
                                  .withAppId(baseHost.getAppId())
                                  .withInfraId(baseHost.getInfraId())
                                  .withHostConnAttr(baseHost.getHostConnAttr());
        if (isValidBastionHostConnectionReference(baseHost)) {
          builder.withBastionConnAttr(baseHost.getBastionConnAttr()).withTags(baseHost.getTags());
        }
        save(builder.build());
      }
    });
  }

  private boolean isValidBastionHostConnectionReference(Host baseHost) {
    return baseHost.getBastionConnAttr() != null && baseHost.getBastionConnAttr().getUuid() != null
        && baseHost.getBastionConnAttr().getUuid().length() > 0;
  }
}
