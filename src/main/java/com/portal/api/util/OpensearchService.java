package com.portal.api.util;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;
import org.springframework.stereotype.Service;

@Service
public class OpensearchService {

	public SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
		SSLContext sslContext = SSLContext.getInstance("SSL");
	    sslContext.init(null, new TrustManager[]{new X509TrustManager() {
	        public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
	        }
	        public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
	        }
	        public X509Certificate[] getAcceptedIssuers() {
	            return new X509Certificate[0];
	        }
	    }}, new java.security.SecureRandom());
	    return sslContext;
    }
	
	public BasicCredentialsProvider getBasicCredentialsProvider() {
		BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
	    credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("admin", "admin"));
	    return credentialsProvider;
	}
	
	public SearchResponse search(SSLContext sslContext, BasicCredentialsProvider credentialsProvider, SearchRequest searchRequest) throws IOException {
	
		// Build the rest client
	    try (RestHighLevelClient client = new RestHighLevelClient(
	            RestClient.builder(new HttpHost("opensearch", 9200, "https"))
	                    .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
	                            .setDefaultCredentialsProvider(credentialsProvider)
	                            .setSSLContext(sslContext)
	                            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)))) {
	
	        // Execute the search request
	    	// #TODO turn search response into a proper response object
	        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
	        return searchResponse;
	    }  
	    
	}
	
	public CountResponse count(SSLContext sslContext, BasicCredentialsProvider credentialsProvider, CountRequest countRequest) throws IOException {
		
		// Build the rest client
	    try (RestHighLevelClient client = new RestHighLevelClient(
	            RestClient.builder(new HttpHost("opensearch", 9200, "https"))
	                    .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
	                            .setDefaultCredentialsProvider(credentialsProvider)
	                            .setSSLContext(sslContext)
	                            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)))) {
	
	    	CountResponse countResponse = client.count(countRequest, RequestOptions.DEFAULT);
	        return countResponse;
	    }  
	    
	}
	
}
