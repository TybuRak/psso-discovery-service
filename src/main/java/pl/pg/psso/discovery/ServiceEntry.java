package pl.pg.psso.discovery;

import java.time.Instant;

/** One registered service: its name and the IP address it registered from. */
public record ServiceEntry(String name, String ip, Instant registeredAt) {
}
