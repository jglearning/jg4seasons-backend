package com.jglearning.jg4seasons.academy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InitialAcademyProperties.class)
class AcademyConfig {}
