# ConcurrentHTTPServer

## Ryan Jin and Addison Goolsbee

A robust HTTP 1.x server written in Java that allows for efficient handling of tens of thousands of simultaneous requests using its multithreaded master-worker symmetric design.

## Usage

- Run the program using the `run` script while within the ConcurrentHTTPServer/ConcurrentHTTPServer directory:
  - To run the default configuration file, use `run`
  - To specify a custom configuration file, add the file path as an argument: `run -config {config_file_path}`
- Test the server with something like `curl -i localhost:8080`

### Configuration File

The configuration file selected with the option `-config {file_path}` must follow the [Apache configuration style](http://httpd.apache.org/docs/2.4/vhosts/examples.html)

The configuration file allows for specification of ports, server names, server resource root folders, and multiple virtual hosts, as well as a few environment variables

The environment variables that must be defined are:

- `Listen`: the port(s) the server will be open on
- `nSelectLoops`: the number of active connections per worker thread

The basic structure of a virtual host is as follows:

```xml
<VirtualHost *:6789>
  DocumentRoot <root dir>
  ServerName <server name>
</VirtualHost>
```

### Example Requests

These will work if you use the default configuration file, used automatically if no configuration file is specified

```bash
curl -i localhost:8080
curl -i localhost:8080/small.txt
curl -i localhost:8080/apsodfunasdf
curl -i localhost:8080/folder
curl -i localhost:8080/folder/a.txt
curl -i -H "Authorization: Basic YWRkaXNvbjpyeWFuX3N1Y2tz" localhost:8080/folder/a.txt
curl -i -H "Authorization: Basic YWRkaXNvbjpyeWFuX3N1Y2t" localhost:8080/folder/a.txt
curl -i addison:ryan_sucks@localhost:8080/folder/a.txt
curl -i -H "User-Agent: iPhone" localhost:8080
curl -i -H "Host: server1.com" localhost:8080
curl -i -H "Host: server2.com" localhost:8080
curl -i -H "Host: server3.com" localhost:8080
curl -i -H "Accept: image/png, image/jpeg" localhost:8080/folder/test.txt
curl -i -H "Accept: image/png, image/jpeg, */*" localhost:8080/folder/test.txt
curl -i -H "Accept: text/*" localhost:8080/test.txt
curl -i -H "Accept: text/html" localhost:8080/test.txt
curl -i -H "If-Modified-Since: Sat, 20 Dec 2023 19:43:31 GMT" localhost:8080
curl -i -H "If-Modified-Since: Sat, 20 Dec 2022 19:43:31 GMT" localhost:8080
curl -i localhost:8080/load

Post Request:
"POST /script.cgi HTTP/1.0" + CRLF
  + "Host: server2.com" + CRLF
  + "Accept: www/source" + CRLF
  + "Accept: text/html" + CRLF
  + "Accept: text/plain" + CRLF
  + "User-Agent: Lynx/2.4 libwww/2.14" + CRLF
  + "Content-type: application/x-www-form-urlencoded" + CRLF
  + "Transfer-Encoding: chunked" + CRLF
  + "Content-length: 30" + CRLF + CRLF
  + "q=Larry Bird&l=35&pass=testing";

Keep Connection Alive Request:

sentence = "GET / HTTP/1.1" + CRLF
  + "Host: server1.com" + CRLF
  + "Accept: text/html, application/xhtml+xml, application/xml;q=0.9, */*;q=0.8" + CRLF
  + "Accept-Language: en-US,en;q=0.5" + CRLF
  + "Cache-Control: no-cache" + CRLF
  + "Connection: keep-alive" + CRLF
  + "Authorization: Bearer myAccessToken123" + CRLF + CRLF;

```

## File Structure

All code is within `src/`, there are 4 important files:

- `Main` is the starting file, which reads in the configuration file and starts up the HTTPServer
- `HTTPServer` is the 'main thread', it spawns the worker thrads, monitors them, and listens for shutdown commands
- `HTTPServerWorkerThread` is the finite state machine of an individual worker, which alternates between accept, reading, read, writing, and write, as specified by the enum `ConnectionState`. It cycles through multiple (nSelectLoops) multiplexed connections, allowing for many requests to be handled simultaneously
- `HTTPRequestHandler` handles the parsing of fully received requests, and then generates the responses of those requests for both GET and POST requests

Other files:

- `ConfigParser/` contains a modified version of [Apache's Java config parser](https://github.com/stackify/apache-config/tree/c7401dcd466a38e89f8853276d3b5c070481b307/src/main/java/com/stackify/apache) to read .conf files into a tree object
- `config.conf` is our default config file if none is provided
- `ChannelTimeOutMonitor` iterates through all existing worker threads and checks that they haven't timed out. It is run every 0.5 seconds in `HTTPServer`
- `ConnectionControlBlock` is essentially an object that contains all information regarding a current request/response, including two ByteBuffers for reading and writing. It maintains the state of the request
- `ConnectionState` is an enum defining the 5 states a ConnectionControlBlock can be in
- `HTTPRequestType` is an enum defining the acceptable HTTP request types (only GET and POST)
- `RequestHandler` is an interface defining how requests should be handled. While we only have one implementation this interface (HTTP), having the interface allows us to be flexible and handle requests in a more modular approach, similar to NGINX
- `ResponseException` is an exception type for HTTP status code errors during the request handler
- `WorkersSyncData` is an object that contains global information about all workers. Every worker has access to this object

## Load Balancing

When a new request is received by the server, a worker thread will initially try to add it to its set of ongoing requests. It will call the `overloaded()` function, and if the current worker has more requests ongoing than the average worker, it will not accept the current request, and instead pass it to the next worker. This is not as efficient as something like NGINX, as one request could potentially be passed between several workers before a worker accepts it, but it is still effective as a load balancing technique. In addition to this automatic load balancing between workers, there is also the problem of if ALL of the workers are at their capacity. To solve this, there is a virtual endpoint `/load` which returns `503` if the server is overloaded and `200` otherwise

## Request Handling

The `HTTPRequestHandler` can handle a diverse range of headers, and works with both GET and POST (can perform CGI) methods. In both the request and the response, every line is separated by a carriage line return feed (\r\n)

### Functional Request Headers

- **First Line**: the first line of any request to the server must be in the format `<METHOD_NAME_ALL_CAPS> <URL> HTTP/1.1`, where the supported method names are **GET** and **POST**
- `Host`: the virtual server to use (multiple web servers can be run on the same host, you can specify them in the configuration file)
- `Accept`: allowed MIME types to be returned
- `User-Agent`: the type of client device. The server may return mobile-optimized html files when the user agent is a mobile device
- `If-Modified-Since`: conditional transfer: a date in which only if the server has modified the resource since, will it return it
- `Connection`: methods for maintaining a connection between client and server. Accepts **close**, **keep-alive**
- `Authorization`: if the requested resource's directory contains a `.htaccess` file, the server requires basic authorization in the format `Basic {base64-encoded string of username:password}`
- `Content-Type`: in the case of a POST request, Content-Type is mandatory so the server knows how to parse the request body. Content-Type can only be application/x-www-form-urlencoded
- `Content-Length`: in the case of a POST request, Content-Length is mandatory to tell the server the length of the request body
- **Request Body**: in the case of a POST request, the request body starts with a double carriage line return feed, and should be in application/x-www-form-urlencoded format and look like the following: item1=A&item2=B

### Response Headers

- **First Line**: the first line of the response comes in the format `HTTP/1.1 <StatusCode> <StatusCodeMessage>`, where the status code and its accompanying message follow standard HTTP guidelines
- `Date`: the current date at the time of sending the request
- `Server`: Addison-Ryan Server Java/1.21 (the name of this server)
- `Last-Modified`: the date the requested resource was last modified, for use with caching using the request header `If-Modified-Since`
- `WWW-Authenticate`: a conditional header that appears if the requested resource required authorization and no authorization header was inputted
- `Content-Type`: the MIME type of content returned
- `Content-Length`: the length in bytes of the response body (excluding headers)
- `Transfer-Encoding`: a conditional header which appears in the case of a POST request. The server uses CGI and the response is in a chunked transfer encoded format
- **Response Body**: the response body begins with a double carriage line return feed, and contains the requested resource, as specified in `Content-Type` and `Content-Length`

### Simple Benchmarking

Maximum throughput: 275 megabytes/sec

`ab -n 10000 -c 10  localhost:8080/large.txt`

Benchmarking localhost (be patient)
Completed 1000 requests
Completed 2000 requests
Completed 3000 requests
Completed 4000 requests
Completed 5000 requests
Completed 6000 requests
Completed 7000 requests
Completed 8000 requests
Completed 9000 requests
Completed 10000 requests
Finished 10000 requests

Server Software:        Addison-Ryan
Server Hostname:        localhost
Server Port:            8080

Document Path:          /large.txt
Document Length:        148579 bytes

Concurrency Level:      10
Time taken for tests:   5.274 seconds
Complete requests:      10000
Failed requests:        0
Total transferred:      1487700000 bytes
HTML transferred:       1485790000 bytes
Requests per second:    1896.13 [#/sec] (mean)
Time per request:       5.274 [ms] (mean)
Time per request:       0.527 [ms] (mean, across all concurrent requests)
Transfer rate:          275475.27 [Kbytes/sec] received

Connection Times (ms)
              min  mean[+/-sd] median   max
Connect:        0    1   0.9      1      31
Processing:     1    4   1.3      3      33
Waiting:        1    3   1.4      2      32
Total:          3    5   1.6      5      35

Percentage of the requests served within a certain time (ms)
  50%      5
  66%      5
  75%      5
  80%      5
  90%      6
  95%      6
  98%      8
  99%     12
 100%     35 (longest request)

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
