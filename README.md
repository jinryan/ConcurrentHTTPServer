# ConcurrentHTTPServer
Select Multiplex Symmetric HTTP Server

## Usage
- Make sure you have **Java** installed
- Install **IntelliJ IDEA**
- Open this project with IntelliJ IDEA to the directory `ConcurrentHTTPServer/ConcurrentHTTPServer`
- Run the project through the IntelliJ IDE
- To specify a custom configuration file, add the argument -config {file_path}

## Code Structure
All code is within `ConcurrentHTTPServer/src`. 

- `Main` is the starting file, which reads in the configuration file and starts up the HTTPServer
- `HTTPServer` Spawns the worker threads and then listens for user input to shutdown
- `HTTPServerWorkerThread` is the finite state machine of an individual worker, which alternates between accept, reading, read, writing, and write, as specified by the enum `ConnectionState`
- Upon reading in an entire HTTP request, as denounced by the double carriage line return feed, `HTTPRequestHandler` is triggered, which processes the input and generates an output, which is then sent back to the worker to write to the socket
- When a worker thread initially accepts a connection, it creates a `ConnectionControlBlock` object to keep track of the state of that connection. It then cycles through other connections, based on nSelectLoops, which is specified in the configuration file

## NGINX Comparisons

### a. Although nginx has both Master and Worker, the design is the symmetric design that we covered in class: multiple Workers compete on the shared welcome socket (accept). One issue about the design we said in class is that this design does not offer flexible control such as load balance. Please describe how nginx introduces mechanisms to allow certain load balancing among workers? Related with the shared accept, one issue is that when a new connection becomes acceptable, multiple workers can be notified, creating contention. Please read nginx event loop and describe how nginx tries to resolve the contention

While it doesn't solve the problem entirely, the OS tends to implement load balancing on its own by distributing incoming connections round-robin to listening processes. This is not a complex enough strategy for load balancing, since the current state of each worker thread is not considered, but it does help some. Instead of using the OS to partially resolve the load balancing problem, NGINX uses something called `ngx_use_accept_mutex`, which is passed between workers, and only the worker currently holding the mutex can accept a connection. In this way, only the worker that can most well handle a new connection receives a new connection

### b. The nginx event loop processes both io events and timers. If it were nginx, how would you implement the 3-second timeout requirement of this project?

In the main event loop, NGINX calls `ngx_event_expire_timers` near the end, which handles timers that have expired. A timer object in NGINX comes with a callback function (event handler), where once the timer expires, the callback function is called. To implement the 3-second timeout, we could create a new timer event with each event loop that is set to 3 seconds, where the timer resets if anything has changed in the event loop. If the timer expires, we would close the connection and send a timeout response

### c. nginx processes HTTP in 11 phases. What are the phases? Please list the checker functions of each phase

1. NGX_HTTP_POST_READ_PHASE: `ngx_http_core_generic_phase`
2. NGX_HTTP_SERVER_REWRITE_PHASE: `ngx_http_core_rewrite_phase`
3. NGX_HTTP_FIND_CONFIG_PHASE: `ngx_http_core_find_config_phase`
4. NGX_HTTP_REWRITE_PHASE: `ngx_http_core_rewrite_phase`
5. NGX_HTTP_POST_REWRITE_PHASE: `ngx_http_core_post_rewrite_phase`
6. NGX_HTTP_PREACCESS_PHASE: `ngx_http_core_generic_phase`
7. NGX_HTTP_ACCESS_PHASE: `ngx_http_core_access_phase`
8. NGX_HTTP_POST_ACCESS_PHASE: `ngx_http_core_post_access_phase`
9. NGX_HTTP_PRECONTENT_PHASE: `ngx_http_core_generic_phase`
10. NGX_HTTP_CONTENT_PHASE: `ngx_http_core_content_phase`
11. NGX_HTTP_LOG_PHASE: None

### d. A main design feature of nginx is efficient support of upstream; that is, forward request to an upstream server. Can you describe the high level design?

Forwarding requests to upstream servers, or reverse proxying, is the ability to send a request to another server, get the response from that server, and then send the response back to the client. This is particularly useful when it comes to load balancing, as one overloaded server handling tens of thousands of requests can send the requests to other servers to allow for horizontal scaling. In NGINX, to configure reverse proxying involves using the upstream module, where you will define one or more upstream servers, and a load balancing algorithm to spread requests between servers

### e. nginx introduces a buffer type ngx_buf_t. Please briefly compare ngx_buf_t vs ByteBuffer we covered for Java nio?

Both buffer types have the purpose of holding temporary data for I/O operations, but they're slightly different: along with being written in C instead of java, ngx_buf_t is more low-level: memory must be managed manually, and data is viewed in raw bytes only, instead of primitive data types as in ByteBuffer. This makes ngx_buf_t a more efficent overall data type, albeit more complicated to use
