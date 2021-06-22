package io.harness.security.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.security.SecurityContextBuilder.PRINCIPAL_NAME;
import static io.harness.security.SecurityContextBuilder.PRINCIPAL_TYPE;
import static io.harness.security.dto.PrincipalType.API_KEY;
import static io.harness.security.dto.PrincipalType.SERVICE_ACCOUNT;

import io.harness.annotations.dev.OwnedBy;

import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SERVICE_ACCOUNT")
@TypeAlias("ServiceAccountPrincipal")
public class ServiceAccountPrincipal extends Principal {
  public ServiceAccountPrincipal(String name) {
    this.type = SERVICE_ACCOUNT;
    this.name = name;
  }

  @Override
  public Map<String, String> getJWTClaims() {
    Map<String, String> claims = new HashMap<>();
    claims.put(PRINCIPAL_TYPE, getType().toString());
    claims.put(PRINCIPAL_NAME, getName());
    return claims;
  }

  public static ServiceAccountPrincipal getPrincipal(Map<String, Claim> claims) {
    return new ServiceAccountPrincipal(
        claims.get(PRINCIPAL_NAME) == null ? null : claims.get(PRINCIPAL_NAME).asString());
  }
}
