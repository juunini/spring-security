/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.config.web.server;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.annotation.web.reactive.ServerHttpSecurityConfigurationBuilder;
import org.springframework.security.config.test.SpringTestContext;
import org.springframework.security.test.web.reactive.server.WebTestClientBuilder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.config.Customizer.withDefaults;

/**
 * @author Rob Winch
 * @since 5.0
 */
public class AuthorizeExchangeSpecTests {

	ServerHttpSecurity http = ServerHttpSecurityConfigurationBuilder.httpWithDefaultAuthentication();

	public final SpringTestContext spring = new SpringTestContext(this);

	@Test
	public void antMatchersWhenMethodAndPatternsThenDiscriminatesByMethod() {
		this.http.csrf().disable().authorizeExchange().pathMatchers(HttpMethod.POST, "/a", "/b").denyAll().anyExchange()
				.permitAll();
		WebTestClient client = buildClient();
		// @formatter:off
		client.get()
				.uri("/a")
				.exchange()
				.expectStatus().isOk();
		client.get()
				.uri("/b")
				.exchange()
				.expectStatus().isOk();
		client.post()
				.uri("/a")
				.exchange()
				.expectStatus().isUnauthorized();
		client.post()
				.uri("/b")
				.exchange()
				.expectStatus().isUnauthorized();
		// @formatter:on
	}

	@Test
	public void antMatchersWhenPatternsThenAnyMethod() {
		this.http.csrf().disable().authorizeExchange().pathMatchers("/a", "/b").denyAll().anyExchange().permitAll();
		WebTestClient client = buildClient();
		// @formatter:off
		client.get()
				.uri("/a")
				.exchange()
				.expectStatus().isUnauthorized();
		client.get()
				.uri("/b")
				.exchange()
				.expectStatus().isUnauthorized();
		client.post()
				.uri("/a")
				.exchange()
				.expectStatus().isUnauthorized();
		client.post()
				.uri("/b")
				.exchange()
				.expectStatus().isUnauthorized();
		// @formatter:on
	}

	@Test
	public void antMatchersWhenPatternsInLambdaThenAnyMethod() {
		this.http.csrf(ServerHttpSecurity.CsrfSpec::disable).authorizeExchange(
				(exchanges) -> exchanges.pathMatchers("/a", "/b").denyAll().anyExchange().permitAll());
		WebTestClient client = buildClient();
		// @formatter:off
		client.get()
				.uri("/a")
				.exchange()
				.expectStatus().isUnauthorized();
		client.get()
				.uri("/b")
				.exchange()
				.expectStatus().isUnauthorized();
		client.post()
				.uri("/a")
				.exchange()
				.expectStatus().isUnauthorized();
		client.post()
				.uri("/b")
				.exchange()
				.expectStatus().isUnauthorized();
		// @formatter:on
	}

	@Test
	public void buildWhenAuthorizationManagerThenWorks() {
		this.spring.register(NoRequestsConfig.class, AuthorizationManagerConfig.class).autowire();
		ReactiveAuthorizationManager<ServerWebExchange> request = (ReactiveAuthorizationManager<ServerWebExchange>) this.spring
				.getContext().getBean("request");
		given(request.verify(any(), any())).willReturn(Mono.empty());
		SecurityWebFilterChain filterChain = this.spring.getContext().getBean(SecurityWebFilterChain.class);
		WebTestClient client = WebTestClientBuilder.bindToWebFilters(filterChain).build();
		// @formatter:off
		client.get()
				.uri("/a")
				.exchange()
				.expectStatus().isOk();
		// @formatter:on
		verify(request).verify(any(), any());
		ReactiveAuthorizationManager<MethodInvocation> method = (ReactiveAuthorizationManager<MethodInvocation>) this.spring
				.getContext().getBean("method");
		verifyNoInteractions(method);
	}

	@Test
	public void antMatchersWhenNoAccessAndAnotherMatcherThenThrowsException() {
		this.http.authorizeExchange().pathMatchers("/incomplete");
		assertThatIllegalStateException()
				.isThrownBy(() -> this.http.authorizeExchange().pathMatchers("/throws-exception"));
	}

	@Test
	public void anyExchangeWhenFollowedByMatcherThenThrowsException() {
		assertThatIllegalStateException().isThrownBy(() ->
		// @formatter:off
			this.http.authorizeExchange()
					.anyExchange().denyAll()
					.pathMatchers("/never-reached")
		// @formatter:on
		);
	}

	@Test
	public void buildWhenMatcherDefinedWithNoAccessThenThrowsException() {
		this.http.authorizeExchange().pathMatchers("/incomplete");
		assertThatIllegalStateException().isThrownBy(this.http::build);
	}

	@Test
	public void buildWhenMatcherDefinedWithNoAccessInLambdaThenThrowsException() {
		this.http.authorizeExchange((exchanges) -> exchanges.pathMatchers("/incomplete"));
		assertThatIllegalStateException().isThrownBy(this.http::build);
	}

	private WebTestClient buildClient() {
		return WebTestClientBuilder.bindToWebFilters(this.http.build()).build();
	}

	@EnableWebFluxSecurity
	static class NoRequestsConfig {

		@Bean
		SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
			// @formatter:off
			return http
					.authorizeExchange(withDefaults())
					.build();
			// @formatter:on
		}

	}

	@Configuration
	static class AuthorizationManagerConfig {

		private final ReactiveAuthorizationManager<ServerWebExchange> request = mock(
				ReactiveAuthorizationManager.class);

		private final ReactiveAuthorizationManager<MethodInvocation> method = mock(ReactiveAuthorizationManager.class);

		@Bean
		ReactiveAuthorizationManager<ServerWebExchange> request() {
			return this.request;
		}

		@Bean
		ReactiveAuthorizationManager<MethodInvocation> method() {
			return this.method;
		}

	}

}
