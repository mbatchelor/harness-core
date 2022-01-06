package io.harness.cvng.analysis.beans;

import io.harness.cvng.core.utils.Thresholds;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;

public enum Risk {
  NO_DATA(-2),
  NO_ANALYSIS(-1),
  HEALTHY(0),
  OBSERVE(1),
  NEED_ATTENTION(2),
  UNHEALTHY(3);
  private static final Map<Integer, Risk> INT_TO_RISK_MAP = new HashMap<>();
  private static final Map<Integer, Risk> INT_TO_RISK_MAP_FOR_DEPLOYMENT_LOG_ANALYSIS = new HashMap<>();
  private static final Map<Integer, Risk> INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS = new HashMap<>();

  static {
    for (Risk r : Risk.values()) {
      INT_TO_RISK_MAP.put(r.value, r);
    }

    INT_TO_RISK_MAP_FOR_DEPLOYMENT_LOG_ANALYSIS.put(0, HEALTHY);
    INT_TO_RISK_MAP_FOR_DEPLOYMENT_LOG_ANALYSIS.put(1, UNHEALTHY);
    // LE doest not send 2 for log analysis, but keeping this entry for backward compatibility
    INT_TO_RISK_MAP_FOR_DEPLOYMENT_LOG_ANALYSIS.put(2, UNHEALTHY);

    INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.put(-2, NO_DATA);
    INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.put(-1, NO_ANALYSIS);
    INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.put(0, HEALTHY);
    INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.put(1, OBSERVE);
    INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.put(2, UNHEALTHY);
    // LE doest not send 3 for deployment timeseries analysis, but as the demo data deserializer converts unhealthy into
    // value 3, that's why adding this entry
    INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.put(3, UNHEALTHY);
  }

  private final int value;
  public int getValue() {
    return value;
  }
  Risk(int value) {
    this.value = value;
  }

  public static Risk valueOf(int risk) {
    Preconditions.checkState(INT_TO_RISK_MAP.containsKey(risk), "Invalid value of risk %s", risk);
    return INT_TO_RISK_MAP.get(risk);
  }

  public static Risk valueOfRiskForDeploymentLogAnalysis(int risk) {
    Preconditions.checkState(
        INT_TO_RISK_MAP_FOR_DEPLOYMENT_LOG_ANALYSIS.containsKey(risk), "Invalid value of risk for deployment %s", risk);
    return INT_TO_RISK_MAP_FOR_DEPLOYMENT_LOG_ANALYSIS.get(risk);
  }

  public static Risk valueOfRiskForDeploymentTimeSeriesAnalysis(int risk) {
    Preconditions.checkState(INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.containsKey(risk),
        "Invalid value of risk for deployment %s", risk);
    return INT_TO_RISK_MAP_FOR_DEPLOYMENT_TIMESERIES_ANALYSIS.get(risk);
  }

  public static Risk getRiskFromRiskScore(double riskScore) {
    Integer healthScore = getHealthScoreFromRiskScore(riskScore);
    if (riskScore == -2.0) {
      return Risk.NO_DATA;
    } else if (riskScore < 0.0) {
      return Risk.NO_ANALYSIS;
    } else if (healthScore >= Thresholds.HEALTHY_PERCENTAGE) {
      return Risk.HEALTHY;
    } else if (healthScore >= Thresholds.OBSERVE_PERCENTAGE) {
      return Risk.OBSERVE;
    } else if (healthScore >= Thresholds.NEED_ATTENTION_PERCENTAGE) {
      return Risk.NEED_ATTENTION;
    } else {
      return Risk.UNHEALTHY;
    }
  }

  public static Integer getHealthScoreFromRiskScore(double riskScore) {
    if (riskScore < 0) {
      return null;
    }
    return Integer.valueOf(100 - (int) Math.round(100 * riskScore));
  }

  public boolean isGreaterThanEq(Risk other) {
    return this.getValue() >= other.getValue();
  }

  public boolean isGreaterThan(Risk other) {
    return this.getValue() > other.getValue();
  }

  public boolean isLessThanEq(Risk other) {
    return this.getValue() <= other.getValue();
  }
}
