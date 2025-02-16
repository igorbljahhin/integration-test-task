package com.example.ecommerceorderprocessor.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {
    private CRM crm = new CRM();
    private Financial financial = new Financial();

    @Data
    public static class Financial {
        @NotEmpty
        private String fileNamePattern = "fin_orders_{datetime:ddMMyyyyHHmm}.csv";
        @Min(1)
        private int maxRecordsPerFile = 1000;
        @NotEmpty
        private String outputDirectory = "./financial-output";
    }

    @Data
    public static class CRM {
        @NotEmpty
        private String apiUrl = "http://localhost:4010";
    }
}
