package org.mifos.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for workflow engine settings.
 * Binds properties from application.properties under the 'workflow' prefix.
 *
 * SECURITY: This class contains only engine-neutral configuration.
 * Flowable-specific properties (database credentials, schema settings)
 * have been moved to FlowableAdapterConfig in the adapter-flowable module
 * to prevent engine credentials from being visible across the whole application.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "workflow")
public class WorkflowConfig {

    private Engine engine = new Engine();
    private Fineract fineract = new Fineract();
    private Authentication authentication = new Authentication();
    private Process process = new Process();

    @Data
    public static class Engine {
        // lowercase to match @ConditionalOnProperty(havingValue="flowable")
        // SECURITY: if this were "FLOWABLE" uppercase, the conditional
        // would not match "flowable" and no adapter bean would be created
        // causing a startup failure with NoSuchBeanDefinitionException
        private String type = "flowable";
    }

    @Data
    public static class Fineract {
        private String baseUrl;
        private String username;
        // SECURITY: password bound from ${FINERACT_PASSWORD} env variable
        // never hardcoded — set in application.properties as ${FINERACT_PASSWORD:password}
        private String password;
        private String tenantId;
        private boolean testEnabled;
        private int connectionTimeout = 30000;
        private int readTimeout = 30000;
    }

    @Data
    public static class Authentication {
        private boolean enabled = true;
        private String authKeyHeader = "Authorization";
        private String authKeyPrefix = "Basic ";
        private int tokenRefreshInterval = 3600;
        private boolean autoRefresh = true;
    }

    @Data
    public static class Process {
        private String defaultAssignee = "system";
        private boolean autoDeploy = true;
        private String processLocation = "classpath:processes/";
        private boolean enableProcessHistory = true;
        private int maxProcessInstances = 1000;
        private int processTimeout = 86400;
    }
}