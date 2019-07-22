package software.wings.graphql.datafetcher;

import static io.harness.exception.WingsException.ReportTarget.GRAPHQL_API;
import static io.harness.exception.WingsException.USER_SRE;
import static software.wings.graphql.datafetcher.DataFetcherUtils.EXCEPTION_MSG_DELIMITER;
import static software.wings.graphql.datafetcher.DataFetcherUtils.GENERIC_EXCEPTION_MSG;
import static software.wings.graphql.datafetcher.DataFetcherUtils.NEGATIVE_LIMIT_ARG_MSG;
import static software.wings.graphql.datafetcher.DataFetcherUtils.NEGATIVE_OFFSET_ARG_MSG;

import com.google.common.collect.Lists;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.ExceptionLogger;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.query.Query;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.directive.DataFetcherDirective.DataFetcherDirectiveAttributes;
import software.wings.graphql.schema.query.QLPageQueryParameterImpl;
import software.wings.graphql.schema.query.QLPageQueryParameters;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractConnectionV2DataFetcher<F, S, O> extends BaseDataFetcher {
  public static final String FILTERS = "filters";
  private static final String SORT_CRITERIA = "sortCriteria";
  private static final String LIMIT = "limit";
  private static final String OFFSET = "offset";

  public void addDataFetcherDirectiveAttributesForParent(
      String parentTypeName, DataFetcherDirectiveAttributes dataFetcherDirectiveAttributes) {
    parentToContextFieldArgsMap.putIfAbsent(parentTypeName, dataFetcherDirectiveAttributes);
  }

  @Override
  public Object get(DataFetchingEnvironment environment) throws Exception {
    try {
      Type[] typeArguments = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments();
      Class<F> filterClass = (Class<F>) typeArguments[0];
      Class<S> sortClass = (Class<S>) typeArguments[1];
      Class<O> returnClass = (Class<O>) typeArguments[2];
      List<F> filters = (List<F>) fetchObject(environment, FILTERS, filterClass);
      final List<S> sort = (List<S>) fetchObject(environment, SORT_CRITERIA, sortClass);
      authRuleInstrumentation.instrumentDataFetcher(this, environment, returnClass);
      QLPageQueryParameters pageQueryParameters = extractPageQueryParameters(environment);
      Map<String, String> contextFieldArgsMap =
          utils.getContextFieldArgsMap(parentToContextFieldArgsMap, environment.getParentType().getName());
      if (contextFieldArgsMap != null) {
        if (filters == null) {
          filters = new ArrayList();
        }
        List<F> finalFilters = filters;
        contextFieldArgsMap.forEach((key, value) -> {
          F filter = generateFilter(environment, key, value);
          finalFilters.add(filter);
        });
        filters.addAll(finalFilters);
      }

      String parentTypeName = environment.getParentType().getName();
      final String accountId = getAccountId(environment);
      AccountThreadLocal.set(accountId);
      return fetch(filters, pageQueryParameters, sort);
    } catch (WingsException ex) {
      throw new WingsException(getCombinedErrorMessages(ex), ex, ex.getReportTargets());
    } catch (Exception ex) {
      throw new WingsException(GENERIC_EXCEPTION_MSG, USER_SRE);
    } finally {
      AccountThreadLocal.unset();
    }
  }

  private String getCombinedErrorMessages(WingsException ex) {
    List<ResponseMessage> responseMessages = ExceptionLogger.getResponseMessageList(ex, GRAPHQL_API);
    return responseMessages.stream().map(rm -> rm.getMessage()).collect(Collectors.joining(EXCEPTION_MSG_DELIMITER));
  }

  protected abstract F generateFilter(DataFetchingEnvironment environment, String key, String value);

  public String getAccountId(DataFetchingEnvironment environment) {
    return utils.getAccountId(environment);
  }

  private QLPageQueryParameters extractPageQueryParameters(DataFetchingEnvironment dataFetchingEnvironment) {
    final Integer offset = dataFetchingEnvironment.getArgument(OFFSET);
    final Integer limit = dataFetchingEnvironment.getArgument(LIMIT);

    if (limit != null && limit < 0) {
      throw new InvalidRequestException(NEGATIVE_LIMIT_ARG_MSG);
    }
    if (offset != null && offset < 0) {
      throw new InvalidRequestException(NEGATIVE_OFFSET_ARG_MSG);
    }

    return QLPageQueryParameterImpl.builder()
        .limit(limit == null ? 50 : limit)
        .offset(offset == null ? 0 : offset)
        .selectionSet(dataFetchingEnvironment.getSelectionSet())
        .build();
  }

  private Object fetchObject(DataFetchingEnvironment dataFetchingEnvironment, String fieldName, Class klass) {
    Object object = dataFetchingEnvironment.getArguments().get(fieldName);
    if (object == null) {
      return null;
    }

    if (object instanceof Collection) {
      Collection returnCollection = Lists.newArrayList();
      Collection collection = (Collection) object;
      collection.forEach(item -> returnCollection.add(convertToObject(item, klass)));
      return returnCollection;
    }
    return convertToObject(object, klass);
  }

  private <M> M convertToObject(Object fromValue, Class<M> klass) {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.convertValue(fromValue, klass);
  }

  protected abstract O fetchConnection(
      List<F> filters, QLPageQueryParameters pageQueryParameters, List<S> sortCriteria);

  protected final O fetch(List<F> filters, QLPageQueryParameters pageQueryParameters, List<S> sortCriteria) {
    try {
      return fetchConnection(filters, pageQueryParameters, sortCriteria);
    } catch (WingsException ex) {
      if (ErrorCode.ACCESS_DENIED.equals(ex.getCode())) {
        logger.warn("User doesn't have access to resource or no entities exist in that app");
      }
      throw ex;
    }
  }

  protected abstract void populateFilters(List<F> filters, Query query);

  @NotNull
  public Query populateFilters(WingsPersistence wingsPersistence, List<F> filters, Class entityClass) {
    Query query = populateAccountFilter(wingsPersistence, entityClass);
    populateFilters(filters, query);
    return query;
  }

  @NotNull
  public Query populateAccountFilter(WingsPersistence wingsPersistence, Class entityClass) {
    Query query = wingsPersistence.createAuthorizedQuery(entityClass);
    final String accountId = getAccountId();
    if (accountId != null) {
      query.filter(SettingAttributeKeys.accountId, accountId);
      return query;
    }
    return query;
  }

  public String getAccountId() {
    return AccountThreadLocal.get();
  }
}
