package com.example.demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder.Builder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class GatewayApplication {

	private static final Logger LOGGER = LoggerFactory.getLogger(GatewayApplication.class);
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Autowired
	private WhiteList whiteList;

	@Bean
	@Order(0)
	GlobalFilter globalFilter0() {
		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest();
			ServerHttpResponse response = exchange.getResponse();
			List<String> token = request.getHeaders().get("token");
			if (whiteList.getWhiteList().contains(request.getURI().getPath())) {
				return chain.filter(exchange);
			} else if (!CollectionUtils.isEmpty(token) && token.get(0).equals("abc")) {
				LOGGER.info("Token: " + token.get(0));
				request = exchange.getRequest().mutate().header("userId", "0").headers(c -> c.remove("token")).build();
				return chain.filter(exchange.mutate().request(request).build());
			} else {
				response.getHeaders().add("Content-Type", "application/json;charset=utf-8");
				Map<String, Object> res = new HashMap<>();
				res.put("code", 403);
				res.put("data", "");
				res.put("message", "invalid token");
				try {
					return response.writeWith(Flux.just(response.bufferFactory().wrap(OBJECT_MAPPER.writeValueAsBytes(res))));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}
				return null;
			}
		};
	}

	@Bean
	@Order(1)
	GlobalFilter globalFilter1() {
		return (exchange, chain) -> {
			LOGGER.info(System.currentTimeMillis() + "");
			return chain.filter(exchange).then(Mono.fromRunnable(() -> {
				HttpHeaders headers = exchange.getResponse().getHeaders();
				headers.add("Access-Control-Allow-Origin", "*");
				headers.add("Access-Control-Allow-Credentials", "true");
				LOGGER.info(System.currentTimeMillis() + "");
			}));
		};
	}

	@Bean
	KeyResolver keyResolver() {
		return (exchange) -> {
			return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
		};
	}

	@Bean
	RateLimiter<?> rateLimiter() {
		return new RedisRateLimiter(4, 16);
	}

	@Bean
	RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
		Builder b = builder.routes();
		// @formatter:off
		b.route("h5", r -> r.path("/h5/**")
				.filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter()).setKeyResolver(keyResolver())))
				.uri("http://192.168.100.252:801"));
		b.route("otc", r -> r.path("/otc/**")
				.filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(rateLimiter()).setKeyResolver(keyResolver())))
				.uri("http://192.168.100.252:801"));
		// @formatter:on
		return b.build();
	}

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

}
