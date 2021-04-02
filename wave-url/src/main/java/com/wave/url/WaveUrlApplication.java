package com.wave.url;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.SpringCloudApplication;

/**
 * wave url application
 * 短链接系统
 */
@SpringCloudApplication
public class WaveUrlApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(WaveUrlApplication.class, args);
    }
}
