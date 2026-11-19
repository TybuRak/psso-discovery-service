package pl.pg.psso.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-memory registry: name -> instances (one per IP). Names are case-insensitive.
 * Several instances under one name give simple load balancing: find() returns a random one.
 * Every change is saved to a JSON file, so a restart of the discovery service keeps the registrations.
 */
@Component
public class ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    private final Map<String, Map<String, ServiceEntry>> services = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final Path storageFile;

    public ServiceRegistry(ObjectMapper mapper, @Value("${discovery.storage-file:registry.json}") String storageFile) {
        this.mapper = mapper;
        this.storageFile = storageFile.isBlank() ? null : Path.of(storageFile);
    }

    /** Loads registrations saved before a restart (same format as GET /services). */
    @PostConstruct
    void load() {
        if (storageFile == null || !Files.exists(storageFile)) {
            return;
        }
        try {
            List<ServiceEntry> saved = mapper.readValue(storageFile.toFile(), new TypeReference<List<ServiceEntry>>() { });
            for (ServiceEntry e : saved) {
                Instant at = e.registeredAt() == null ? Instant.now() : e.registeredAt();
                services.computeIfAbsent(normalize(e.name()), k -> new ConcurrentHashMap<>())
                        .put(e.ip(), new ServiceEntry(normalize(e.name()), e.ip(), at));
            }
            log.info("Loaded {} instance(s) from {}", saved.size(), storageFile.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not read {}: {}", storageFile.toAbsolutePath(), e.getMessage());
        }
    }

    private synchronized void save() {
        if (storageFile == null) {
            return;
        }
        try {
            Path tmp = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), findAll());
            Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Could not save {}: {}", storageFile.toAbsolutePath(), e.getMessage());
        }
    }

    /** Adds an instance or refreshes it when the same name and IP are registered again. Returns true when it is new. */
    public boolean register(String name, String ip) {
        String key = normalize(name);
        Map<String, ServiceEntry> instances = services.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        boolean created = instances.put(ip, new ServiceEntry(key, ip, Instant.now())) == null;
        save();
        return created;
    }

    /** Removes every instance of the service. */
    public boolean unregister(String name) {
        boolean removed = services.remove(normalize(name)) != null;
        save();
        return removed;
    }

    /** Removes one instance; the service disappears when its last instance is removed. */
    public boolean unregister(String name, String ip) {
        boolean[] removed = {false};
        services.computeIfPresent(normalize(name), (key, instances) -> {
            removed[0] = instances.remove(ip) != null;
            return instances.isEmpty() ? null : instances;
        });
        save();
        return removed[0];
    }

    /** Random instance of the service (load balancing), or empty when nothing is registered. */
    public Optional<ServiceEntry> find(String name) {
        List<ServiceEntry> instances = instances(name);
        if (instances.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(instances.get(ThreadLocalRandom.current().nextInt(instances.size())));
    }

    public List<ServiceEntry> instances(String name) {
        Map<String, ServiceEntry> instances = services.get(normalize(name));
        if (instances == null) {
            return List.of();
        }
        return instances.values().stream().sorted(Comparator.comparing(ServiceEntry::ip)).toList();
    }

    /** All instances of all services, sorted by name and IP. */
    public List<ServiceEntry> findAll() {
        return services.values().stream()
                .flatMap(instances -> instances.values().stream())
                .sorted(Comparator.comparing(ServiceEntry::name).thenComparing(ServiceEntry::ip))
                .toList();
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
