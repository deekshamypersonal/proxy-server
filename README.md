# Multithreaded Proxy Server

## Overview

This project is a **Multithreaded HTTP/HTTPS Proxy Server** implemented in Java. It intercepts client HTTP/HTTPS requests, forwards them to the target server, and then returns the response back to the client. The server uses a **thread pool** for handling multiple concurrent clients efficiently. Additionally, an **LRU Cache** is implemented to speed up responses for frequently requested pages, and there’s a built-in mechanism to block specific hosts at runtime.

## Key Features

- **Multithreading & Thread Pooling**:  
  Uses a configurable thread pool to handle multiple concurrent client requests without blocking new connections.

- **LRU Caching**:  
  Employs an **LRU (Least Recently Used) Cache** to store previously requested content. On subsequent requests for the same resource, the server can return the cached response, reducing latency and improving performance.

- **Dynamic Host Blocking**:  
  Maintains a set of blocked hosts that can be updated at runtime. An admin thread runs in the background, allowing you to add new blocked domains without restarting the server. Blocked hosts return a **403 Forbidden** response.

- **Support for HTTPS (Tunneling)**:  
  Implements HTTPS tunneling via the `CONNECT` method. The proxy establishes a direct tunnel between the client and target server, ensuring encrypted traffic remains private.


## How It Works

1. **Client Requests**: A client (e.g., browser or Postman) sends an HTTP/HTTPS request to the proxy.
2. **Request Handling**: The proxy checks if the requested host is blocked. If so, it responds with a **403 Forbidden** error.  
   Otherwise, it checks the LRU cache for a stored response.
3. **Cache Miss**: If the resource isn’t cached, the proxy fetches the resource from the origin server, stores it in the cache, and returns it to the client.
4. **Cache Hit**: On subsequent requests for the same resource, the proxy serves the cached version directly, significantly reducing the response time.
5. **HTTPS Tunneling**: For HTTPS requests, the proxy establishes a TCP tunnel without decrypting the data, ensuring secure end-to-end encryption.

## Demo

**Setup**:  
1. Run the proxy server on port `8080`.
2. Configure client (e.g., Postman) to use `localhost:8080` as the proxy.

### 1. Cache Miss vs Cache Hit

- **First Request (Cache Miss)**:  
  When you hit a URL (e.g., `http://neverssl.com`) for the first time via Postman, the proxy must fetch it from the origin server.  
  - *Expected Result*: Longer response time, as the response is not cached yet.
    
  Cache Miss Screenshot

  <img width="608" alt="image" src="https://github.com/user-attachments/assets/1451334c-3ec0-420a-9b40-4166761d379d">

  <img width="354" alt="image" src="https://github.com/user-attachments/assets/26e16a57-28bd-4f75-aab5-a8fe4fe2d052">



- **Second Request (Cache Hit)**:  
  Sending the same request again should return the response much faster, since it’s now served from the cache.  
  - *Expected Result*: Shorter response time on the second request.
   
  Cache Hit Screenshot

  <img width="627" alt="image" src="https://github.com/user-attachments/assets/a19c57f3-4d21-453a-9e44-bd7028d3867a">

  <img width="395" alt="image" src="https://github.com/user-attachments/assets/63702439-496a-4776-88ba-7194043838e3">



### 2. Blocked URL  
If you add a host to the blocked list using the admin console (e.g., `http://httpbin.org`), and then request `http://httpbin.org` again:

- *Expected Result*: The proxy should return a **403 Forbidden** response.

  Blocked URL Screenshot
  
  <img width="568" alt="image" src="https://github.com/user-attachments/assets/57dedf97-57c4-474f-ac89-019bd8ae6bc5">




