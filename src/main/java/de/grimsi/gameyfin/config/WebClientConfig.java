package de.grimsi.gameyfin.config;

import io.netty.handler.logging.LogLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

@Slf4j
@Configuration
public class WebClientConfig implements WebClientCustomizer {

    @Override
    public void customize(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .wiretap(this.getClass().getCanonicalName(), LogLevel.TRACE, AdvancedByteBufFormat.TEXTUAL) // Enable full request / response logging in TRACE
                .proxyWithSystemProperties(); // Enable use of system proxy

        webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    /**
     * This fixes the wrong Content-Type in responses of the IGDB API by overwriting it so the WebClient is able to parse it automatically
     * They return "application/protobuf", correct would be "application/x-protobuf"
     *
     * @return the filter function
     */
    public static ExchangeFilterFunction fixProtobufContentTypeInterceptor() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse ->
                Mono.just(clientResponse.mutate()
                        .headers(headers -> headers.remove(HttpHeaders.CONTENT_TYPE))
                        .header(HttpHeaders.CONTENT_TYPE, String.valueOf(ProtobufHttpMessageConverter.PROTOBUF))
                        .build())
        );
    }
}