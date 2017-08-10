package software.wings.service.impl.expression;

import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.SearchFilter.Operator.EQ;
import static software.wings.beans.SearchFilter.Operator.IN;
import static software.wings.dl.PageRequest.Builder.aPageRequest;
import static software.wings.dl.PageRequest.UNLIMITED;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.commons.collections.CollectionUtils;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.dl.PageRequest;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by sgurubelli on 8/7/17.
 */
@Singleton
public class ServiceExpressionBuilder extends ExpressionBuilder {
  @Inject private ServiceVariableService serviceVariablesService;

  @Inject private ServiceTemplateService serviceTemplateService;

  @Override
  public List<String> getExpressions(String appId, String entityId) {
    List<String> expressions = new ArrayList<>();
    expressions.addAll(getStaticExpressions());
    expressions.addAll(getDynamicExpressions(appId, entityId));
    expressions.addAll(getServiceTemplateVariableExpressions(appId, entityId));
    return expressions;
  }

  @Override
  public List<String> getDynamicExpressions(String appId, String entityId) {
    PageRequest<ServiceVariable> pageRequest = aPageRequest().withLimit(UNLIMITED).build();
    pageRequest.addFilter("appId", appId, EQ);
    pageRequest.addFilter("entityId", entityId, EQ);
    pageRequest.addFilter("entityType", SERVICE, EQ);

    List<ServiceVariable> serviceVariables = serviceVariablesService.list(pageRequest, true);
    if (CollectionUtils.isNotEmpty(serviceVariables)) {
      return serviceVariables.stream()
          .map(serviceVariable -> "serviceVariable." + serviceVariable.getName())
          .collect(Collectors.toList());
    }
    return Arrays.asList();
  }

  private List<String> getServiceTemplateVariableExpressions(String appId, String serviceId) {
    PageRequest<ServiceTemplate> pageRequest = aPageRequest().withLimit(UNLIMITED).build();
    pageRequest.addFilter("appId", appId, EQ);
    pageRequest.addFilter("serviceId", serviceId, EQ);

    List<ServiceTemplate> serviceTemplates = serviceTemplateService.list(pageRequest, false, false);
    if (CollectionUtils.isNotEmpty(serviceTemplates)) {
      List<String> serviceTemplateIds =
          serviceTemplates.stream().map(ServiceTemplate::getUuid).collect(Collectors.toList());
      PageRequest<ServiceVariable> variablePageRequest = aPageRequest().withLimit(UNLIMITED).build();
      pageRequest.addFilter("appId", appId, EQ);
      pageRequest.addFilter("entityId", serviceTemplateIds, IN);
      List<ServiceVariable> serviceVariables = serviceVariablesService.list(variablePageRequest, true);
      if (CollectionUtils.isNotEmpty(serviceVariables)) {
        // return serviceVariables.stream().map(ServiceVariable::getName).collect(Collectors.toList());
        return serviceVariables.stream()
            .map(serviceVariable -> "serviceVariable." + serviceVariable.getName())
            .collect(Collectors.toList());
      }
    }
    return Arrays.asList();
  }
}
