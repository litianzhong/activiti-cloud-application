package org.activiti.cloud.runtime;

import org.activiti.cloud.starter.rb.configuration.ActivitiRuntimeBundle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ActivitiRuntimeBundle
@ComponentScan("org.activiti.cloud.services.common.security")
public class RuntimeBundleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuntimeBundleApplication.class,
                              args);
    }
}