package com.portal.api.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.stereotype.Service;

import com.portal.api.exception.ResourceNotFoundException;

@Service
public class HttpService {

	public static String sendHttpPostRequest(String url, String requestBody, String bearerToken) throws Exception {
        // Set the URL for the POST request
        URL apiUrl = new URL(url);

        // Open a connection
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();

        // Set the request method to POST
        connection.setRequestMethod("POST");

        // Set the Authorization header
        connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        
        // Set the content type header
        connection.setRequestProperty("Content-Type", "application/json");

        // Enable input and output streams
        connection.setDoInput(true);
        connection.setDoOutput(true);

        // Send the request body if needed
        if (requestBody != null) {
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(requestBody.getBytes());
            outputStream.flush();
        }

        // Get the response
        int responseCode = connection.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        // Print the response
        //System.out.println("Response Code: " + responseCode);
        //System.out.println("Response Body: " + response.toString());

        // Close the connection
        connection.disconnect();
        
        return response.toString();
    }
	
	public static String sendHttpGetRequest(String url, String bearerToken) throws Exception {
        // Set the URL for the POST request
        URL apiUrl = new URL(url);

        // Open a connection
        HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();

        // Set the request method to POST
        connection.setRequestMethod("GET");

        // Set the Authorization header
        connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        
        // Set the content type header
        connection.setRequestProperty("Content-Type", "application/json");

        // Enable input and output streams
        connection.setDoInput(true);
        connection.setDoOutput(true);
        
     // Get the response code
        int responseCode = connection.getResponseCode();
        
     // Check if the response code is 404 (Not Found)
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            System.out.println("Resource not found: " + url);
            throw new ResourceNotFoundException("Resource not found");
        }

        // Get the response
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        // Print the response
        //System.out.println("Response Code: " + responseCode);
        //System.out.println("Response Body: " + response.toString());

        // Close the connection
        connection.disconnect();
        
        return response.toString();
    }
}
