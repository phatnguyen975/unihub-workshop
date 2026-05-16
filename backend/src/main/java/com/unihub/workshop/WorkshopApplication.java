package com.unihub.workshop;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkshopApplication {

	public static void main(String[] args) {
		Dotenv dotenv = null;
		try {
			dotenv = Dotenv.configure()
					.directory(".")
					.ignoreIfMissing()
					.load();
		} catch (Exception ignored) {
			// Ignore missing or unreadable .env in production
		}

		if (dotenv != null) {
			for (DotenvEntry entry : dotenv.entries()) {
				String key = entry.getKey();
				if (System.getProperty(key) == null && System.getenv(key) == null) {
					System.setProperty(key, entry.getValue());
				}
			}
		}

		SpringApplication.run(WorkshopApplication.class, args);
	}
}
