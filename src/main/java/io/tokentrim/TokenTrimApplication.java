package io.tokentrim;

import io.tokentrim.config.TokenTrimProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
@EnableConfigurationProperties(TokenTrimProperties.class)
public class TokenTrimApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenTrimApplication.class, args);
    }
}
