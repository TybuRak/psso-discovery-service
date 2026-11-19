package pl.pg.psso.discovery;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/services")
@CrossOrigin
public class DiscoveryController {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryController.class);

    private final ServiceRegistry registry;

    public DiscoveryController(ServiceRegistry registry) {
        this.registry = registry;
    }

    /**
     * Registers an instance. Body: {"name": "transport"}; IP taken from the connection (optional "ip" overrides it).
     * The same name from another IP adds a second instance; the same name and IP again only refreshes it.
     */
    @PostMapping
    public ResponseEntity<?> register(@RequestBody(required = false) RegisterRequest body, HttpServletRequest request) {
        if (body == null || body.name() == null || body.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "name is required");
        }
        String ip = (body.ip() == null || body.ip().isBlank()) ? request.getRemoteAddr() : body.ip().trim();
        boolean created = registry.register(body.name(), ip);
        ServiceEntry entry = registry.instances(body.name()).stream()
                .filter(e -> e.ip().equals(ip)).findFirst().orElseThrow();
        log.info("{} {} -> {} (instances: {})", created ? "REGISTER" : "RE-REGISTER", entry.name(), entry.ip(),
                registry.instances(entry.name()).size());
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(entry);
    }

    /** Without ?ip= removes the whole service, with ?ip= only that instance. */
    @DeleteMapping("/{name}")
    public ResponseEntity<?> unregister(@PathVariable String name, @RequestParam(required = false) String ip) {
        boolean removed = (ip == null || ip.isBlank()) ? registry.unregister(name) : registry.unregister(name, ip.trim());
        if (!removed) {
            return error(HttpStatus.NOT_FOUND, ip == null || ip.isBlank()
                    ? "service not found: " + name
                    : "instance not found: " + name + " " + ip);
        }
        log.info("UNREGISTER {}{}", name, ip == null || ip.isBlank() ? " (all instances)" : " " + ip);
        return ResponseEntity.noContent().build();
    }

    /** One instance chosen at random: simple client-side load balancing. */
    @GetMapping("/{name}")
    public ResponseEntity<?> find(@PathVariable String name) {
        return registry.find(name)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "service not found: " + name));
    }

    /** All instances of one service. */
    @GetMapping("/{name}/instances")
    public ResponseEntity<?> instances(@PathVariable String name) {
        List<ServiceEntry> instances = registry.instances(name);
        if (instances.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "service not found: " + name);
        }
        return ResponseEntity.ok(instances);
    }

    @GetMapping
    public List<ServiceEntry> findAll() {
        return registry.findAll();
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
