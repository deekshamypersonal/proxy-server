# Multithreaded Proxy Server

![Java](https://img.shields.io/badge/Language-Java-orange.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![Status](https://img.shields.io/badge/Status-Active-green.svg)

## Overview

This project is a **Multithreaded HTTP/HTTPS Proxy Server** implemented in Java. It intercepts client HTTP/HTTPS requests, forwards them to the target server, and then returns the response back to the client. The server uses a **thread pool** for handling multiple concurrent clients efficiently. Additionally, an **LRU Cache** is implemented to speed up responses for frequently requested pages, and there’s a built-in mechanism to block specific hosts at runtime.

## Key Features

- **Multithreading & Thread Pooling**:  
  Uses a configurable thread pool to handle multiple concurrent client requests without blocking new connections. This ensures the server can scale to handle higher loads.

- **LRU Caching**:  
  Employs an **LRU (Least Recently Used) Cache** to store previously requested content. On subsequent requests for the same resource, the server can return the cached response, reducing latency and improving performance.

- **Dynamic Host Blocking**:  
  Maintains a set of blocked hosts that can be updated at runtime. An administrative thread runs in the background, allowing you to add new blocked domains without restarting the server. Blocked hosts return a **403 Forbidden** response.

- **Support for HTTPS (Tunneling)**:  
  Implements HTTPS tunneling via the `CONNECT` method. The proxy establishes a direct tunnel between the client and target server, ensuring encrypted traffic remains private.

- **Administrative Interface via Console**:  
  An admin thread reads from standard input, enabling you to dynamically add blocked URLs or gracefully shut down the server.

- **Graceful Shutdown**:  
  Ensures that ongoing tasks are completed before shutting down, preventing abrupt termination and potential data corruption.

## How It Works

1. **Client Requests**: A client (e.g., browser or Postman) sends an HTTP/HTTPS request to the proxy.
2. **Request Handling**: The proxy checks if the requested host is blocked. If so, it responds with a **403 Forbidden** error.  
   Otherwise, it checks the LRU cache for a stored response.
3. **Cache Miss**: If the resource isn’t cached, the proxy fetches the resource from the origin server, stores it in the cache, and returns it to the client.
4. **Cache Hit**: On subsequent requests for the same resource, the proxy serves the cached version directly, significantly reducing the response time.
5. **HTTPS Tunneling**: For HTTPS requests, the proxy establishes a TCP tunnel without decrypting the data, ensuring secure end-to-end encryption.

## Limitations

- **No Authentication Support**: Currently, the proxy does not implement authentication for clients.
- **No Advanced Caching Policies**: Caching does not honor HTTP Cache-Control headers. It simply uses a capacity-based LRU policy.
- **Limited Robustness**: Error handling is basic. More sophisticated error recovery, logging, or load balancing features could be added.
- **Blocking is Hostname-based Only**: The blocking feature relies on hostnames. IP-based blocking or pattern-based blocking is not implemented.
- **No SSL Interception**: The proxy does not decrypt HTTPS traffic; it merely tunnels it, so features like URL-based blocking on HTTPS are limited.

## Demo

**Setup**:  
1. Run the proxy server on port `8080`.
2. Configure your client (e.g., Postman) to use `localhost:8080` as the proxy.

### 1. Cache Miss vs Cache Hit

- **First Request (Cache Miss)**:  
  When you hit a URL (e.g., `http://example.com`) for the first time via Postman, the proxy must fetch it from the origin server.  
  - *Expected Result*: Longer response time, as the response is not cached yet.
  
  *Screenshot Example*:  
  ![Cache Miss Screenshot](./screenshots/cache_miss.png)

- **Second Request (Cache Hit)**:  
  Sending the same request again should return the response much faster, since it’s now served from the cache.  
  - *Expected Result*: Shorter response time on the second request.
  
  *Screenshot Example*:  
  ![Cache Hit Screenshot](./screenshots/cache_hit.png)

### 2. Blocked URL  
If you add a host to the blocked list using the admin console (e.g., `example.com`), and then request `http://example.com` again:

- *Expected Result*: The proxy should return a **403 Forbidden** response.

  *Screenshot Example*:  
  ![Blocked URL Screenshot](./screenshots/blocked_url.png)

### 3. HTTPS Request  
When you attempt to access `https://example.com`, the proxy sets up a tunnel. The initial `CONNECT` request is responded to with `200 Connection Established`, and all subsequent encrypted communication passes through the proxy.

  *Screenshot Example*:  
  ![HTTPS Tunneling Screenshot](./screenshots/https_tunneling.png)

## Getting Started

1. **Build & Run**:  
   ```bash
   javac -cp .:libs/* org/test3/*.java
   java -cp .:libs/* org.test3.ProxyServer 8080
