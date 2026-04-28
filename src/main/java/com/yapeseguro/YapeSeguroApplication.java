// ============================================================
// YapeSeguroApplication.java — Entry Point
// ============================================================
package com.yapeseguro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // necesario para Feature #7: pagos programados
public class YapeSeguroApplication {
    public static void main(String[] args) {
        SpringApplication.run(YapeSeguroApplication.class, args);
    }
}
