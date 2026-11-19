package pl.pg.psso.discovery;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory registry name -> service. Names are case-insensitive. */
@Component
public class ServiceRegistry {

    private final Map<String, ServiceEntry> services = new ConcurrentHashMap<>();

    /** Registers or overwrites a service. Returns true when the name was not registered before. */
    public boolean register(String name, String ip) {
        String key = normalize(name);
        return services.put(key, new ServiceEntry(key, ip, Instant.now())) == null;
    }

    public boolean unregister(String name) {
        return services.remove(normalize(name)) != null;
    }

    public Optional<ServiceEntry> find(String name) {
        return Optional.ofNullable(services.get(normalize(name)));
    }

    public List<ServiceEntry> findAll() {
        return services.values().stream()
                .sorted(Comparator.comparing(ServiceEntry::name))
                .toList();
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
