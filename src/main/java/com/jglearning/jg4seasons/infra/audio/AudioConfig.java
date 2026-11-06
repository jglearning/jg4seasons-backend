package com.jglearning.jg4seasons.infra.audio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AudioProperties.class)
class AudioConfig {}
