package com.nexxserve.nexxauth.service;

import tools.jackson.databind.ObjectMapper;
import com.nexxserve.nexxauth.dto.response.LogEntryResponse;
import com.nexxserve.nexxauth.entity.LogCategory;
import com.nexxserve.nexxauth.entity.LogEntry;
import com.nexxserve.nexxauth.entity.LogLevel;
import com.nexxserve.nexxauth.entity.Platform;
import com.nexxserve.nexxauth.repository.LogEntryRepository;
import com.nexxserve.nexxauth.repository.OrganisationRepository;
import com.nexxserve.nexxauth.repository.PlatformRepository;
import com.nexxserve.nexxauth.util.ClientIps;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central logging service: persists log entries, broadcasts them to connected
 * SSE clients, and provides query/filter endpoints using JPA Specifications
 * for dynamic filter composition.
 */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);

    private final LogEntryRepository logEntryRepository;
    private final PlatformRepository platformRepository;
    private final OrganisationRepository organisationRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** In-memory cache of organisation id → slug to avoid repeated lookups. */
    private final Map<Long, String> orgSlugCache = new ConcurrentHashMap<>();

    public LogService(LogEntryRepository logEntryRepository,
                      PlatformRepository platformRepository,
                      OrganisationRepository organisationRepository,
                      ObjectMapper objectMapper) {
        this.logEntryRepository = logEntryRepository;
        this.platformRepository = platformRepository;
        this.organisationRepository = organisationRepository;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public LogEntry logEvent(LogLevel level, LogCategory category, String eventType,
                             String message, Long platformId, Long organisationId,
                             String actor, String detail, String clientKey, String domainOverride) {
        HttpServletRequest request = currentRequest();
        String ip = request != null ? ClientIps.resolve(request, false) : null;
        String domain = domainOverride != null ? domainOverride
                : (request != null ? extractDomain(request) : null);
        String requestId = MDC.get("requestId");

        // Resolve platform ID from the request path if not provided
        Long resolvedPlatformId = platformId;
        if (resolvedPlatformId == null && request != null) {
            resolvedPlatformId = extractPlatformIdFromRequest(request);
        }

        LogEntry entry = new LogEntry();
        entry.setPlatformId(resolvedPlatformId);
        entry.setOrganisationId(organisationId);
        entry.setLevel(level);
        entry.setCategory(category);
        entry.setEventType(eventType);
        entry.setMessage(message);
        entry.setActor(actor);
        entry.setIp(ip);
        entry.setRequestId(requestId);
        entry.setDetail(detail);
        entry.setClientKey(clientKey);
        entry.setDomain(domain);

        LogEntry saved = logEntryRepository.save(entry);
        broadcast(saved);
        return saved;
    }

    private Long extractPlatformIdFromRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String[] parts = path.split("/");
        // Path format: /{platformSlug}/...
        if (parts.length >= 2 && !parts[1].isBlank()) {
            String slug = parts[1];
            Platform platform = platformRepository.findBySlug(slug).orElse(null);
            return platform != null ? platform.getId() : null;
        }
        return null;
    }

    /** Extract the domain from the Host header (e.g. "api.example.com"). */
    private String extractDomain(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) return null;
        // Strip port if present (e.g. "localhost:8080" -> "localhost")
        int colon = host.lastIndexOf(":");
        return colon > 0 ? host.substring(0, colon) : host;
    }

    // ------------------------------------------------------------------
    // Querying (JPA Specification-based — dynamic filter composition)
    // ------------------------------------------------------------------

    public Page<LogEntryResponse> query(Long platformId, Long organisationId,
                                        LogLevel level, LogCategory category,
                                        String eventType, String clientKey, String domain,
                                        Instant from, Instant to,
                                        Pageable pageable) {
        Specification<LogEntry> spec = Specification
                .where(hasPlatformId(platformId))
                .and(hasOrganisationId(organisationId))
                .and(hasLevel(level))
                .and(hasCategory(category))
                .and(hasEventType(eventType))
                .and(hasClientKey(clientKey))
                .and(hasDomain(domain))
                .and(createdBetween(from, to));

        Page<LogEntry> page = logEntryRepository.findAll(spec, pageable);
        return page.map(this::toResponse);
    }

    // --- Specification helpers ---

    private Specification<LogEntry> hasPlatformId(Long platformId) {
        if (platformId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("platformId"), platformId);
    }

    private Specification<LogEntry> hasOrganisationId(Long organisationId) {
        if (organisationId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("organisationId"), organisationId);
    }

    private Specification<LogEntry> hasLevel(LogLevel level) {
        if (level == null) return null;
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    private Specification<LogEntry> hasCategory(LogCategory category) {
        if (category == null) return null;
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    private Specification<LogEntry> hasEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("eventType"), eventType);
    }

    private Specification<LogEntry> hasClientKey(String clientKey) {
        if (clientKey == null) return null;
        if ("__none__".equals(clientKey)) {
            return (root, query, cb) -> cb.isNull(root.get("clientKey"));
        }
        return (root, query, cb) -> cb.equal(root.get("clientKey"), clientKey);
    }

    private Specification<LogEntry> hasDomain(String domain) {
        if (domain == null) return null;
        if ("__none__".equals(domain)) {
            return (root, query, cb) -> cb.isNull(root.get("domain"));
        }
        return (root, query, cb) -> cb.equal(root.get("domain"), domain);
    }

    private Specification<LogEntry> createdBetween(Instant from, Instant to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"), from, to);
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            } else {
                return cb.lessThanOrEqualTo(root.get("createdAt"), to);
            }
        };
    }

    // ------------------------------------------------------------------
    // SSE
    // ------------------------------------------------------------------

    public SseEmitter subscribe(Long platformId, Long organisationId) {
        String key = emitterKey(platformId, organisationId);
        SseEmitter emitter = new SseEmitter(0L);

        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(key, emitter));
        emitter.onTimeout(() -> removeEmitter(key, emitter));
        emitter.onError(e -> removeEmitter(key, emitter));

        return emitter;
    }

    private void broadcast(LogEntry entry) {
        LogEntryResponse response = toResponse(entry);
        if (entry.getPlatformId() != null) {
            sendToEmitters(emitterKey(entry.getPlatformId(), null), response);
            if (entry.getOrganisationId() != null) {
                sendToEmitters(emitterKey(entry.getPlatformId(), entry.getOrganisationId()), response);
            }
        }
    }

    private void sendToEmitters(String key, LogEntryResponse response) {
        List<SseEmitter> list = emitters.get(key);
        if (list == null || list.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log")
                        .data(objectMapper.writeValueAsString(response)));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        list.removeAll(dead);
    }

    private void removeEmitter(String key, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(key);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private LogEntryResponse toResponse(LogEntry entry) {
        return new LogEntryResponse(
                entry.getId(),
                entry.getOrganisationId(),
                resolveOrgSlug(entry.getOrganisationId()),
                entry.getLevel().name(),
                entry.getCategory() != null ? entry.getCategory().name() : "AUTH",
                entry.getEventType(),
                entry.getMessage(),
                entry.getActor(),
                entry.getIp(),
                entry.getRequestId(),
                entry.getDetail(),
                entry.getClientKey(),
                entry.getDomain(),
                entry.getCreatedAt()
        );
    }

    /** Resolve an organisation id to its slug, using an in-memory cache. */
    private String resolveOrgSlug(Long organisationId) {
        if (organisationId == null) return null;
        return orgSlugCache.computeIfAbsent(organisationId, id ->
                organisationRepository.findById(id)
                        .map(o -> o.getSlug())
                        .orElse("unknown")
        );
    }

    private static String emitterKey(Long platformId, Long organisationId) {
        if (organisationId == null) return String.valueOf(platformId);
        return platformId + ":" + organisationId;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
