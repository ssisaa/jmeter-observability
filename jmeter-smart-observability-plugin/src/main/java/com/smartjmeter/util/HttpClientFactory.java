package com.smartjmeter.util;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory that produces a JDK {@link HttpClient} which either verifies
 * TLS chains normally (default) or trusts all certificates for
 * self-signed lab / on-prem Splunk endpoints when {@code insecure=true}.
 *
 * <p>An INFO log is emitted whenever the insecure client is created so
 * the operator can spot production misuse in JMeter logs.</p>
 */
public final class HttpClientFactory {

    private static final Logger LOG = Logger.getLogger(HttpClientFactory.class.getName());

    private HttpClientFactory() { }

    public static HttpClient create(boolean insecure) {
        return create(insecure, Duration.ofSeconds(10));
    }

    public static HttpClient create(boolean insecure, Duration connectTimeout) {
        HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (insecure) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{TRUST_ALL}, new java.security.SecureRandom());
                b.sslContext(ctx);
                LOG.log(Level.WARNING,
                        "TLS verification disabled for Splunk endpoint - only use this for self-signed lab HEC/Search");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to install trust-all TLS context - falling back to default", e);
            }
        }
        return b.build();
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };
}
