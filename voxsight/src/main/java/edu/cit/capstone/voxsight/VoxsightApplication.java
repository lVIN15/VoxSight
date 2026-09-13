package edu.cit.capstone.voxsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

@SpringBootApplication
public class VoxsightApplication {

	public static void main(String[] args) {
		loadDotenv();
		SpringApplication.run(VoxsightApplication.class, args);
	}

	private static void loadDotenv() {
		File[] candidates = new File[] {
			new File(".env"),
			new File("voxsight/.env"),
			new File("../.env")
		};
		for (File file : candidates) {
			if (file.exists() && file.isFile()) {
				try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
					String line;
					while ((line = reader.readLine()) != null) {
						line = line.trim();
						if (line.isEmpty() || line.startsWith("#")) continue;
						int eqIdx = line.indexOf('=');
						if (eqIdx > 0) {
							String key = line.substring(0, eqIdx).trim();
							String val = line.substring(eqIdx + 1).trim();
							if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
								val = val.substring(1, val.length() - 1);
							} else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
								val = val.substring(1, val.length() - 1);
							}
							if (System.getProperty(key) == null && System.getenv(key) == null) {
								System.setProperty(key, val);
							}
						}
					}
					System.out.println("[VoxSight] Loaded environment variables from " + file.getAbsolutePath());
				} catch (Exception e) {
					System.err.println("[VoxSight] Warning: Could not read .env file: " + e.getMessage());
				}
				break;
			}
		}
	}
}
