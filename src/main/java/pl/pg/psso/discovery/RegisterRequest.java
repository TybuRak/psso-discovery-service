package pl.pg.psso.discovery;

/**
 * Body of POST /services. Only the name is required; the IP is read from the connection.
 * The optional "ip" field overrides it (e.g. when registering with curl from another computer).
 */
public record RegisterRequest(String name, String ip) {
}
