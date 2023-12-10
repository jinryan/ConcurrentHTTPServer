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

