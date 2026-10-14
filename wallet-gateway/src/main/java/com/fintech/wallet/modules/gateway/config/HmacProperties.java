package com.fintech.wallet.modules.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "security.hmac")
@Getter
@Setter
public class HmacProperties {
    private List<String> protectedPaths = new ArrayList<>();
}
