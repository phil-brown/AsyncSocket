# AsyncSocket

This is the master NIO Async Socket class. Supports UDP and TCP, IPv4 and IPv6, and both server and client connections.

> Note: I created this code over a year ago. I will add to the documentation once I re-figure out how to use everything.

# How to use

1. Drop the `self.philbrown.asyncsocket` and `org.apache.http.conn.util` packages into your `src` directory. If you are developing on *Android*, you do not need the *Apache* package.
2. A server can be created with:

	    AsyncSocket socket = AsyncSocket.initTcpIPv6();
	    socket.setSocketListener(new AsyncSocket.Listener() {
	        @Override
	        public void onDataReceived(AsyncSocket server, byte[] data) {
	            System.out.println("New Data Received: " + new String(data));
	        }
	
	        @Override
	        public void onDataSent(AsyncSocket client, byte[] data) {
	            //TODO
	        }
	    });
	    try {
	        socket.bindToPort(port);
	    } catch (IOException e) {
	        System.out.println("Unable to listen on busy port " + port);
	    }

## License


    Copyright 2012 Phil Brown

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.