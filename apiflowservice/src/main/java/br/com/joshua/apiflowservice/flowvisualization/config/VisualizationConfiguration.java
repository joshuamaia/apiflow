package br.com.joshua.apiflowservice.flowvisualization.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VisualizationProperties.class)
public class VisualizationConfiguration {
}
