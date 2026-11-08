package com.mentorhub.telegram.antifraud;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ReelsAccessibilityProbe {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    private final RestClient restClient;

    public ReelsAccessibilityProbe() {
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build();
    }

    public ReelsAccessibilityProbe(RestClient restClient) {
        this.restClient = restClient;
    }

    public ProbeResult verifyAccessibility(String canonicalUrl) {
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            return new ProbeResult(false, 400, "URL is empty");
        }

        try {
            var response = restClient.get()
                    .uri(canonicalUrl)
                    .retrieve()
                    .toBodilessEntity();

            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful() || status.value() == 301 || status.value() == 302) {
                return new ProbeResult(true, status.value(), null);
            }
            return new ProbeResult(false, status.value(), "Ссылка возвращает статус: " + status.value());
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Reels URL not found (404): {}", canonicalUrl);
            return new ProbeResult(false, 404, "Ролик не найден или удален (404)");
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("Instagram Rate Limit 429 when checking {}", canonicalUrl);
            return new ProbeResult(true, 429, "TIMEOUT_UNVERIFIED: Превышен лимит запросов сети (429)");
        } catch (ResourceAccessException ex) {
            log.warn("Network timeout/access error while verifying Reels {}: {}", canonicalUrl, ex.getMessage());
            return new ProbeResult(true, 0, "TIMEOUT_UNVERIFIED: Таймаут сети");
        } catch (Exception ex) {
            log.warn("Unexpected error validating Reels {}: {}", canonicalUrl, ex.getMessage());
            return new ProbeResult(true, 0, "TIMEOUT_UNVERIFIED: Не удалось проверить ссылку");
        }
    }

    public record ProbeResult(boolean isValid, int httpStatus, String note) {}
}
