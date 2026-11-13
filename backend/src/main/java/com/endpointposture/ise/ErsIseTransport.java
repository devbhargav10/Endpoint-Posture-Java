package com.endpointposture.ise;

import com.endpointposture.ise.config.IseProperties;
import com.endpointposture.ise.config.IseHttpClientFactory;
import com.endpointposture.security.SecureXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpStatusCodeException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ISE ERS REST (Context-In API) implementation of {@link IseTransport}.
 *
 * <p>
 * Wraps the same two operations the Python prototype's
 * {@code ers_transport.py} implements: {@link #publishPosture} writes a
 * posture fact as a custom endpoint attribute for ISE's own Authorization
 * Policy to act on, and {@link #publishEnforcement} requests either an
 * ANC quarantine/clear or an attribute-write-plus-CoA-reauth, depending
 * on {@code app.ise.enforcement-mode}. Neither is ever called except from
 * {@link IseActionService}, itself reachable only through an explicit
 * operator action — this class makes no enforcement decision on its own.
 * </p>
 */

@Component
public class ErsIseTransport implements IseTransport {

    private static final Logger log = LoggerFactory.getLogger(ErsIseTransport.class);

    private final IseProperties props;
    private final RestClient restClient;
    private final String basicAuthHeader;

    public ErsIseTransport(IseProperties props) {
        this.props = props;
        this.basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(
                (props.getUsername() + ":" + props.getPassword()).getBytes(StandardCharsets.UTF_8));

        if (!props.isConfigured()) {
            // No base URL yet - every method below checks isConfigured()
            // again before using this, so a null client here is safe.
            this.restClient = null;
            return;
        }

        RestClient.Builder builder = RestClient.builder().baseUrl(props.getBaseUrl());
        HttpClient httpClient = IseHttpClientFactory.create(props);
        if (httpClient == null) httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, props.getRequestTimeoutMs())));
        builder = builder.requestFactory(requestFactory);
        this.restClient = builder.build();
    }

    @Override
    public boolean reachable() {
        if (restClient == null)
            return false;
        try {
            restClient.get()
                    .uri("/ers/config/endpoint?filter=mac.EQ.00:00:00:00:00:00&size=1")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    /**
     * @return the ERS internal endpoint id for this MAC, or {@code null} if ISE has
     *         no record of it yet
     */

    @SuppressWarnings("unchecked")
    private String endpointIdFor(String mac) {
        Map<String, Object> body = restClient.get()
                .uri("/ers/config/endpoint?filter=mac.EQ.{mac}", mac)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .retrieve()
                .body(Map.class);
        if (body == null)
            return null;

        Object searchResult = body.get("SearchResult");
        if (!(searchResult instanceof Map<?, ?> sr))
            return null;
        Object resources = sr.get("resources");
        if (!(resources instanceof List<?> list) || list.isEmpty())
            return null;
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> resource))
            return null;
        Object id = resource.get("id");
        return id == null ? null : id.toString();
    }

    @Override
    public IseResult publishPosture(String mac, String status, String details) {
        if (!props.isConfigured()) {
            return new IseResult(false, "ISE is not configured (app.ise.base-url is blank)");
        }
        try {
            Map<String, Object> attrs = Map.of(
                    "ExternalComplianceStatus", status,
                    "PostureLastChecked", Instant.now().toString(),
                    "PostureFailedChecks", (details == null || details.isBlank()) ? "none" : details);

            Map<String, Object> endpointBody = new HashMap<>();
            endpointBody.put("mac", mac);
            endpointBody.put("customAttributes", Map.of("customAttributes", attrs));

            String existingId = endpointIdFor(mac);
            if (existingId != null) {
                endpointBody.put("id", existingId);
                restClient.put()
                        .uri("/ers/config/endpoint/{id}", existingId)
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(Map.of("ERSEndPoint", endpointBody))
                        .retrieve()
                        .toBodilessEntity();
            } else {
                restClient.post()
                        .uri("/ers/config/endpoint")
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(Map.of("ERSEndPoint", endpointBody))
                        .retrieve()
                        .toBodilessEntity();
            }
            return new IseResult(true, "Posture shared with ISE as " + status);
        } catch (Exception e) {
            log.warn("publishPosture failed for {}: {}", mac, e.getMessage());
            return new IseResult(false, e.getMessage(), isAmbiguous(e), isConnectFailure(e));
        }
    }

    @Override
    public IseResult publishEnforcement(String mac, EnforcementAction action, String policy) {
        if (!props.isConfigured()) {
            return new IseResult(false, "ISE is not configured (app.ise.base-url is blank)");
        }
        try {
            return props.getEnforcementMode() == IseProperties.Mode.ANC
                    ? applyAnc(mac, action, policy)
                    : applyAttributeAndReauth(mac, action);
        } catch (Exception e) {
            log.warn("publishEnforcement({}) failed for {}: {}", action, mac, e.getMessage());
            return new IseResult(false, e.getMessage(), isAmbiguous(e), isConnectFailure(e));
        }
    }

    private boolean isAmbiguous(Exception e) {
        if (e instanceof HttpStatusCodeException statusError && statusError.getStatusCode().is5xxServerError())
            return true;
        if (!(e instanceof ResourceAccessException)) return false;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.http.HttpConnectTimeoutException
                    || cause instanceof java.net.ConnectException
                    || cause instanceof java.net.UnknownHostException
                    || cause instanceof javax.net.ssl.SSLException) return false;
            if (cause instanceof java.net.SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException)
                return true;
        }
        // Connection reset/EOF after a request may mean ISE acted but its response was lost.
        return true;
    }

    private boolean isConnectFailure(Exception e) {
        if (e instanceof HttpStatusCodeException) return false;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.ConnectException || cause instanceof java.net.NoRouteToHostException
                    || cause instanceof java.net.UnknownHostException
                    || cause instanceof java.net.http.HttpConnectTimeoutException) return true;
        }
        return false;
    }

    @Override
    public String effectivePolicy(String requestedPolicy) {
        if (props.getEnforcementMode() != IseProperties.Mode.ANC) return requestedPolicy;
        return requestedPolicy == null || requestedPolicy.isBlank() ? props.getAncPolicyName() : requestedPolicy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IseReconciliationResult reconcile(IseOperation operation) {
        if (!props.isConfigured()) return inconclusive("ISE is not configured");
        try {
            if ("SHARE_POSTURE".equals(operation.actionType())) {
                String id = endpointIdFor(operation.macAddress());
                if (id == null) return failed("ISE endpoint has no shared posture attributes");
                Map<String, Object> response = restClient.get().uri("/ers/config/endpoint/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                        .header(HttpHeaders.ACCEPT, "application/json")
                        .retrieve().body(Map.class);
                Map<String, Object> endpoint = nestedMap(response, "ERSEndPoint");
                Map<String, Object> custom = nestedMap(endpoint, "customAttributes");
                Map<String, Object> attrs = nestedMap(custom, "customAttributes");
                boolean matches = operation.postureStatus().equals(stringValue(attrs.get("ExternalComplianceStatus")))
                        && operation.postureDetails().equals(stringValue(attrs.get("PostureFailedChecks")));
                return matches ? succeeded("ISE endpoint posture attributes match the intended operation")
                        : failed("ISE endpoint posture attributes do not match the intended operation");
            }

            if (props.getEnforcementMode() != IseProperties.Mode.ANC)
                return inconclusive("CoA reauthentication has no persistent endpoint state to read back");

            EnforcementAction action = "RESTRICT".equals(operation.actionType())
                    ? EnforcementAction.RESTRICT : EnforcementAction.CLEAR;
            Map<String, Object> response = restClient.get()
                    .uri("/ers/config/ancendpoint?filter=macAddress.EQ.{mac}&size=100", operation.macAddress())
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve().body(Map.class);
            Object searchResult = response == null ? null : response.get("SearchResult");
            Object resourceValue = searchResult instanceof Map<?, ?> search ? search.get("resources") : null;
            if (!(resourceValue instanceof List<?> resources))
                return inconclusive("ISE ANC endpoint lookup returned an unreadable response");
            if (resources.isEmpty()) return action == EnforcementAction.CLEAR
                    ? succeeded("ISE confirms no ANC policy is applied")
                    : failed("ISE confirms no ANC policy is applied");
            if (resources.size() > 1)
                return inconclusive("ISE returned multiple ANC resources for the endpoint");

            Object first = resources.get(0);
            if (!(first instanceof Map<?, ?> resource) || resource.get("id") == null)
                return inconclusive("ISE ANC endpoint lookup did not return a resource ID");
            Map<String, Object> detail = restClient.get()
                    .uri("/ers/config/ancendpoint/{id}", resource.get("id"))
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve().body(Map.class);
            Map<String, Object> anc = nestedMap(detail, "ERSAncEndpoint");
            String policyName = stringValue(anc.get("policyName"));
            if (!operation.macAddress().equalsIgnoreCase(stringValue(anc.get("macAddress"))))
                return inconclusive("ISE ANC resource MAC address did not match the requested endpoint");
            if (action == EnforcementAction.CLEAR)
                return failed("ISE still reports ANC policy " + policyName + " on the endpoint");
            String intended = effectivePolicy(operation.policy());
            return intended.equals(policyName)
                    ? succeeded("ISE confirms ANC policy " + policyName + " is applied")
                    : failed("ISE reports ANC policy " + policyName + " instead of " + intended);
        } catch (Exception e) {
            log.warn("Could not reconcile ISE operation {}: {}", operation.id(), e.getMessage());
            return inconclusive("ISE state readback failed: " + e.getMessage());
        }
    }

    private Map<String, Object> nestedMap(Map<String, Object> value, String key) {
        if (value == null || !(value.get(key) instanceof Map<?, ?> nested)) return Map.of();
        Map<String, Object> result = new HashMap<>();
        nested.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private String stringValue(Object value) { return value == null ? null : value.toString(); }

    private IseReconciliationResult succeeded(String detail) {
        return new IseReconciliationResult(IseReconciliationResult.Outcome.SUCCEEDED, detail);
    }

    private IseReconciliationResult failed(String detail) {
        return new IseReconciliationResult(IseReconciliationResult.Outcome.FAILED, detail);
    }

    private IseReconciliationResult inconclusive(String detail) {
        return new IseReconciliationResult(IseReconciliationResult.Outcome.INCONCLUSIVE, detail);
    }

    private IseResult applyAnc(String mac, EnforcementAction action, String policy) {
        String path = action == EnforcementAction.RESTRICT
                ? "/ers/config/ancendpoint/apply"
                : "/ers/config/ancendpoint/clear";

        List<Map<String, String>> data = new ArrayList<>();
        data.add(Map.of("name", "macAddress", "value", mac));
        if (action == EnforcementAction.RESTRICT) {
            String policyName = effectivePolicy(policy);
            data.add(Map.of("name", "policyName", "value", policyName));
        }

        restClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Map.of("OperationAdditionalData", Map.of("additionalData", data)))
                .retrieve()
                .toBodilessEntity();

        return new IseResult(true, action == EnforcementAction.RESTRICT ? "ANC_APPLIED" : "ANC_CLEARED");
    }

    private IseResult applyAttributeAndReauth(String mac, EnforcementAction action) {
        if (action == EnforcementAction.CLEAR) {
            // Attribute mode has no standing ANC restriction to remove -
            // clearing means "share compliant posture again", which is
            // its own explicit Share Posture action. Matches
            // ers_transport.py's ENFORCEMENT_MODE == "attribute" path,
            // which never implements a CLEAR beyond that.
            return new IseResult(true,
                    "CLEAR_RESTRICTION requested under attribute mode - there is no ANC policy to remove here. "
                            + "Share this endpoint's current posture to have ISE re-evaluate access on next reauth.");
        }

        Map<String, String> session = sessionDetailFor(mac);
        if (session.isEmpty()) {
            return new IseResult(false, "NO_ACTIVE_SESSION - facts stored, will apply on next connect");
        }

        String psn = session.getOrDefault("acs_server", session.get("server"));
        if (psn == null || psn.isBlank()) {
            return new IseResult(false, "SESSION_FOUND_BUT_NO_PSN - cannot trigger CoA, check manually");
        }

        String response = restClient.get()
                .uri("/admin/API/mnt/CoA/Reauth/{psn}/{mac}/1", psn, mac)
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .header(HttpHeaders.ACCEPT, "application/xml")
                .retrieve()
                .body(String.class);

        boolean fired = response != null && response.contains("<results>true</results>");
        return new IseResult(fired, fired ? "REAUTH_APPLIED" : "CoA call returned no confirmation");
    }

    /**
     * Best-effort flat map of every leaf field in ISE's MNT session-detail XML for
     * one MAC.
     */
    private Map<String, String> sessionDetailFor(String mac) {
        try {
            String xml = restClient.get()
                    .uri("/admin/API/mnt/Session/MACAddress/{mac}", mac)
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                    .header(HttpHeaders.ACCEPT, "application/xml")
                    .retrieve()
                    .body(String.class);
            if (xml == null || xml.isBlank())
                return Map.of();

            Document doc = SecureXml.parse(xml);

            Map<String, String> fields = new HashMap<>();
            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Element el = (Element) all.item(i);
                if (el.getChildNodes().getLength() == 1
                        && el.getFirstChild() != null
                        && el.getFirstChild().getNodeValue() != null
                        && !el.getFirstChild().getNodeValue().isBlank()) {
                    fields.put(el.getTagName(), el.getFirstChild().getNodeValue().trim());
                }
            }
            return fields;
        } catch (Exception e) {
            log.warn("Could not fetch ISE session detail for {}: {}", mac, e.getMessage());
            return Map.of();
        }
    }
}
