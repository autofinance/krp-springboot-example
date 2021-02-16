package autofin.eda.restproxy.consumer;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@Configuration
public class RestClientConfig {
    @Value("${restclient.connect.timeout.ms}")
    private int connectTimeout;
    @Value("${restclient.connection-request.timeout.ms}")
    private int connectionRequestTimeout;
    @Value("${restclient.read.timeout.ms}")
    private int connectionReadTimeout;
    /**
     * A RestTemplate that doesn't do hostname verification.
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    @Bean
    public RestTemplate restTemplate() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLContext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .setDefaultRequestConfig(new RequestConfig() {
                    @Override
                    public int getSocketTimeout() {
                        return connectTimeout;
                    }
                })
                .build();
        HttpComponentsClientHttpRequestFactory customRequestFactory = new HttpComponentsClientHttpRequestFactory();
        customRequestFactory.setHttpClient(httpClient);
        customRequestFactory.setConnectTimeout(connectTimeout);
        customRequestFactory.setConnectionRequestTimeout(connectionRequestTimeout);
        customRequestFactory.setReadTimeout(connectionReadTimeout);

        return new RestTemplate(customRequestFactory);
    }
}
