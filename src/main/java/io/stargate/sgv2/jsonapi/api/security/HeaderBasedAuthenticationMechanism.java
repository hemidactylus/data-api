/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.stargate.sgv2.jsonapi.api.security;

import static io.stargate.sgv2.jsonapi.config.constants.HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME;
import static io.stargate.sgv2.jsonapi.config.constants.HttpConstants.DEPRECATED_AUTHENTICATION_TOKEN_HEADER_NAME;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.stargate.sgv2.jsonapi.api.security.challenge.impl.ErrorChallengeSender;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.inject.Instance;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Implementation of the {@link HttpAuthenticationMechanism} that authenticates on a header. If a
 * target header exists, request is considered as authenticated.
 *
 * @see HeaderIdentityProvider
 */
public class HeaderBasedAuthenticationMechanism implements HttpAuthenticationMechanism {

  /** The name of the header to be used for the authentication. */
  private final String headerName;

  /** Customize the challenge. */
  private final Instance<ErrorChallengeSender> customChallengeSender;

  public HeaderBasedAuthenticationMechanism(
      String headerName, Instance<ErrorChallengeSender> customChallengeSender) {
    this.headerName = headerName;
    this.customChallengeSender = customChallengeSender;
  }

  /** Validate Header existence */
  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    // Data API Authentication header key: Token
    String headerValue = context.request().getHeader(headerName);
    // Data API Authentication header key: token
    if (null == headerValue) {
      headerValue = context.request().getHeader(AUTHENTICATION_TOKEN_HEADER_NAME.toLowerCase());
    }
    // Data API supports X-Cassandra-Token for backward compatibility.
    if (null == headerValue) {
      headerValue = context.request().getHeader(DEPRECATED_AUTHENTICATION_TOKEN_HEADER_NAME);
    }
    if (null != headerValue) {
      HeaderAuthenticationRequest request =
          new HeaderAuthenticationRequest(headerName, headerValue);
      HttpSecurityUtils.setRoutingContextAttribute(request, context);
      return identityProviderManager.authenticate(request);
    }
    // No suitable header has been found in this requests
    return Uni.createFrom().optional(Optional.empty());
  }

  /** {@inheritDoc} */
  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom()
        .item(new ChallengeData(HttpResponseStatus.UNAUTHORIZED.code(), null, null));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Writes a custom response.
   */
  @Override
  public Uni<Boolean> sendChallenge(RoutingContext context) {
    // if we should not custo   mize use default
    if (!customChallengeSender.isResolvable()) {
      return HttpAuthenticationMechanism.super.sendChallenge(context);
    }
    return getChallenge(context).flatMap(c -> customChallengeSender.get().apply(context, c));
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Collections.singleton(HeaderAuthenticationRequest.class);
  }

  /** {@inheritDoc} */
  @Override
  public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
    HttpCredentialTransport transport =
        new HttpCredentialTransport(HttpCredentialTransport.Type.OTHER_HEADER, headerName);
    return Uni.createFrom().item(transport);
  }
}
