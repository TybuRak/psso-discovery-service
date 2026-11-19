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

    /** Register (or re-register) a service. Body: {"name": "transport"}; IP taken from the connection. */
    @PostMapping
    public ResponseEntity<?> register(@RequestBody(required = false) RegisterRequest body, HttpServletRequest request) {
        if (body == null || body.name() == null || body.name().isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "name is required");
        }
        String ip = (body.ip() == null || body.ip().isBlank()) ? request.getRemoteAddr() : body.ip().trim();
        boolean created = registry.register(body.name(), ip);
        ServiceEntry entry = registry.find(body.name()).orElseThrow();
        log.info("{} {} -> {}", created ? "REGISTER" : "RE-REGISTER", entry.name(), entry.ip());
        return ResponseEntity.status(created ? HttpStatus.CREATED : HttpStatus.OK).body(entry);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> unregister(@PathVariable String name) {
        if (!registry.unregister(name)) {
            return error(HttpStatus.NOT_FOUND, "service not found: " + name);
        }
        log.info("UNREGISTER {}", name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{name}")
    public ResponseEntity<?> find(@PathVariable String name) {
        return registry.find(name)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "service not found: " + name));
    }

    @GetMapping
    public List<ServiceEntry> findAll() {
        return registry.findAll();
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
