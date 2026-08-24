package com.example.productionmvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ProductionMvpApplication {

	/**
	 * The clock the whole application works in.
	 *
	 * <p>Times are stored and sent as LocalDateTime — a wall-clock reading with no timezone
	 * attached — so whoever reads them assumes their own. That is fine while the server and the
	 * people using it share a clock, and quietly wrong the moment they do not: a hosting
	 * platform runs in UTC, so a task started at 16:20 in the workshop was recorded as 13:20,
	 * and every screen showed it as three hours old the instant it was created.
	 *
	 * <p>Rather than convert eighty timestamp fields to timezone-aware types, the application
	 * runs on the workshop's clock. This is a single-site system, so there is one right answer;
	 * override TZ if the site is somewhere else.
	 */
	private static final String DEFAULT_TIMEZONE = "Europe/Kyiv";

	public static void main(String[] args) {
		// Before Spring starts, so nothing has read the clock yet.
		String zone = System.getenv("TZ");
		TimeZone.setDefault(TimeZone.getTimeZone(
				zone == null || zone.isBlank() ? DEFAULT_TIMEZONE : zone));

		SpringApplication.run(ProductionMvpApplication.class, args);
	}

}
