package software.wings.graphql.datafetcher.cloudefficiencyevents;

import com.google.inject.Inject;

import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;
import lombok.extern.slf4j.Slf4j;
import software.wings.graphql.datafetcher.AbstractStatsDataFetcher;
import software.wings.graphql.datafetcher.billing.QLCCMAggregationFunction;
import software.wings.graphql.datafetcher.cloudefficiencyevents.QLEventsDataPoint.QLEventsDataPointBuilder;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class EventsStatsDataFetcher
    extends AbstractStatsDataFetcher<QLCCMAggregationFunction, QLEventsDataFilter, QLCCMGroupBy, QLEventsSortCriteria> {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject private EventsDataQueryBuilder eventsDataQueryBuilder;

  @Override
  protected QLData fetch(String accountId, QLCCMAggregationFunction aggregateFunction, List<QLEventsDataFilter> filters,
      List<QLCCMGroupBy> groupBy, List<QLEventsSortCriteria> sort) {
    try {
      if (timeScaleDBService.isValid()) {
        return getEventsData(accountId, filters, sort);
      } else {
        throw new InvalidRequestException("Cannot process request in EventsStatsDataFetcher");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching CE Events data {}", e);
    }
  }

  private QLEventData getEventsData(
      String accountId, List<QLEventsDataFilter> filters, List<QLEventsSortCriteria> sortCriteria) {
    CEEventsQueryMetaData queryData;
    ResultSet resultSet = null;
    queryData = eventsDataQueryBuilder.formQuery(accountId, filters, sortCriteria);
    logger.info("EventsStatsDataFetcher query!! {}", queryData.getQuery());

    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(queryData.getQuery());
      return generateEventsData(queryData, resultSet);
    } catch (SQLException e) {
      logger.error("EventsStatsDataFetcher Error exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }

  private QLEventData generateEventsData(CEEventsQueryMetaData queryData, ResultSet resultSet) throws SQLException {
    List<QLEventsDataPoint> dataPointList = new ArrayList<>();
    while (null != resultSet && resultSet.next()) {
      QLEventsDataPointBuilder eventDataBuilder = QLEventsDataPoint.builder();
      for (CEEventsQueryMetaData.CEEventsMetaDataFields field : queryData.getFieldNames()) {
        switch (field) {
          case STARTTIME:
            eventDataBuilder.time(resultSet.getTimestamp(field.getFieldName(), utils.getDefaultCalendar()).getTime());
            break;
          case EVENTDESCRIPTION:
            eventDataBuilder.details(resultSet.getString(field.getFieldName()));
            break;
          case COSTEVENTTYPE:
            eventDataBuilder.type(resultSet.getString(field.getFieldName()));
            break;
          case COSTEVENTSOURCE:
            eventDataBuilder.source(resultSet.getString(field.getFieldName()));
            break;
          default:
            break;
        }
      }
      dataPointList.add(eventDataBuilder.build());
    }
    return QLEventData.builder().data(dataPointList).build();
  }

  @Override
  protected QLData postFetch(String accountId, List<QLCCMGroupBy> groupByList, QLData qlData) {
    return null;
  }

  @Override
  public String getEntityType() {
    return null;
  }
}
