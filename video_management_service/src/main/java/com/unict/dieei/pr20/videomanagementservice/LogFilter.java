package com.unict.dieei.pr20.videomanagementservice;

import com.unict.dieei.pr20.videomanagementservice.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LogFilter extends GenericFilterBean {

    @Autowired
    LogService logService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Cache request and response
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper((HttpServletRequest)request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper((HttpServletResponse)response);

        // Timestamp arrival time
        long arrivalTime = System.currentTimeMillis();

        // Perform request
        filterChain.doFilter(requestWrapper, responseWrapper);

        // Timestamp finish time
        long finishTime = System.currentTimeMillis();

        // Get eventual communication delay
        long responseTime;
        if(responseWrapper.containsHeader("Communication-Delay")) {
            long communicationDelay = Long.parseLong(responseWrapper.getHeader("Communication-Delay"));
            responseTime = finishTime - arrivalTime - communicationDelay;
        }
        else {
            responseTime = finishTime - arrivalTime;
        }

        // Get logs info
        String requestUri = requestWrapper.getRequestURI();
        if(requestUri.equals("/error")) {
            byte[] byteArrayResponse = responseWrapper.getContentAsByteArray();
            if(byteArrayResponse.length > 0) {
                String stringResponse = new String(byteArrayResponse, StandardCharsets.UTF_8);
                int startPathIndex = stringResponse.indexOf("\"path\"") + 8;
                int finishPathIndex = stringResponse.length() - 2;
                requestUri = stringResponse.substring(startPathIndex, finishPathIndex);
            }
        }
        String api = requestWrapper.getMethod() + " " + requestUri;

        long requestId = Long.parseLong(requestWrapper.getHeader("X-REQUEST-ID").replace(".", ""));
        int inputPayloadSize = requestWrapper.getContentLength();
        if(inputPayloadSize == -1) {
            inputPayloadSize = requestWrapper.getContentAsByteArray().length;
        }
        int statusCode = responseWrapper.getStatus();
        String contentLength = responseWrapper.getHeader("Content-Length");
        int outputPayloadSize;
        if(contentLength == null) {
            outputPayloadSize = responseWrapper.getContentAsByteArray().length;
            responseWrapper.setContentLength(outputPayloadSize);
        }
        else {
            outputPayloadSize = Integer.parseInt(contentLength);
        }

        // IMPORTANT: Copy content of cached response back into original response
        responseWrapper.copyBodyToResponse();

        // Save logs to DB
        logService.addLog(api, inputPayloadSize, outputPayloadSize, responseTime, statusCode, requestId);
    }
}
