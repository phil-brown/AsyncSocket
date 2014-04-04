package self.philbrown.asyncsocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
/*
 * Copyright 2012 Phil Brown
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Set;

import org.apache.http.conn.util.InetAddressUtils;

/**
 * This is the master NIO Async Socket class. Supports UDP and TCP, IPv4 and IPv6, and
 * both server and client connections.
 * <br>
 * In its current implementation, this class does not check the current binded or connected status of
 * a socket, and thus Exceptions can arise if a binded socket attempts to connect to a remote port.
 * @author Phil Brown
 *
 */
public class AsyncSocket 
{

	/** {@code true} if the socket should be configured in the IPv6 family. {@code false} for IPv4. */
	private boolean ipv6;
	
	/** {@code true} if the socket should be initialized to read and write TCP packets. {@code false} for UDP. */
	private boolean tcp;
	
	/** The Channel that reads in the data through a {@link ByteBuffer}. */
	private Channel channel;
	
	/** The Selector used to listen forsocket events. */
	private Selector selector;
	
	/** 
	 * The delegate Object that listens for selector events. Upon a new event, 
	 * the involved data is dispatched to this listener. 
	 */
	private Listener delegate;
	
	/** 
	 * The key that is registered through {@link #selector} on the {@link #channel},
	 * and is used to identify the event. 
	 */
	private SelectionKey readKey, writeKey;
	
	/**
	 * If this is set to {@code true}, the run loop will run when this {@link Runnable} is invoked.
	 */
	private boolean isLooping = false;
	
	/**
	 * This is the buffer into which data is received and read by the {@link #channel}
	 */
	private ByteBuffer channelBuffer;
	
	/**
	 * The IP Address of the opened socket
	 */
	private InetAddress address;
	
	/**
	 * The port of the socket
	 */
	private int port;
	
	/**
	 * Constructor. This is made private to force initialization using static methods.
	 * @param longest required length of a datagram or packet, or greater for ample overkill.
	 * @see #initIPv4()
	 * @see #initIPv6()
	 * @see #initIPv4(int)
	 * @see #initIPv6(int)
	 */
	private AsyncSocket(int bufferSize)
	{
		if (bufferSize <= 0)
			bufferSize = 8192;//Plenty for most projects.
		channelBuffer = ByteBuffer.allocate(bufferSize);
	}
	
	/**
	 * Initializes a new TCP, IPv4 socket
	 * @return the initialized server
	 */
	public static AsyncSocket initTcpIPv4()
	{
		AsyncSocket socket = new AsyncSocket(512);
		socket.ipv6 = false;
		socket.tcp = true;
		
		return socket;
	}
	
	/**
	 * Initializes a new UDP, IPv4 socket
	 * @return the initialized server
	 */
	public static AsyncSocket initUdpIPv4()
	{
		AsyncSocket socket = new AsyncSocket(512);
		socket.ipv6 = false;
		socket.tcp = false;
		
		return socket;
	}
	
	/**
	 * Initializes a new TCP, IPv6 socket
	 * @return the initialized server
	 */
	public static AsyncSocket initTcpIPv6()
	{
		AsyncSocket socket = new AsyncSocket(512);
		socket.ipv6 = true;
		socket.tcp = true;
		
		return socket;
	}
	
	/**
	 * Initializes a new UDP, IPv6 socket
	 * @return the initialized server
	 */
	public static AsyncSocket initUdpIPv6()
	{
		AsyncSocket socket = new AsyncSocket(512);
		socket.ipv6 = true;
		socket.tcp = false;
		
		return socket;
	}
	
	/**
	 * Initializes a new TCP, IPv4 socket
	 * @param bufferSize longest required length of a packet, or greater for ample overkill.
	 * @return the initialized server
	 */
	public static AsyncSocket initTcpIPv4(int bufferSize)
	{
		AsyncSocket socket = new AsyncSocket(bufferSize);
		socket.ipv6 = false;
		socket.tcp = true;
		
		return socket;
	}
	
	/**
	 * Initializes a new UDP, IPv4 socket
	 * @param bufferSize longest required length of a datagram, or greater for ample overkill.
	 * @return the initialized server
	 */
	public static AsyncSocket initUdpIPv4(int bufferSize)
	{
		AsyncSocket socket = new AsyncSocket(bufferSize);
		socket.ipv6 = false;
		socket.tcp = false;
		
		return socket;
	}
	
	/**
	 * Initializes a new TCP, IPv6 socket
	 * @param bufferSize longest required length of a packet, or greater for ample overkill.
	 * @return the initialized server
	 */
	public static AsyncSocket initTcpIPv6(int bufferSize)
	{
		AsyncSocket socket = new AsyncSocket(bufferSize);
		socket.ipv6 = true;
		socket.tcp = true;
		
		return socket;
	}
	
	/**
	 * Initializes a new UDP, IPv6 socket
	 * @param bufferSize longest required length of a datagram, or greater for ample overkill.
	 * @return the initialized server
	 */
	public static AsyncSocket initUdpIPv6(int bufferSize)
	{
		AsyncSocket socket = new AsyncSocket(bufferSize);
		socket.ipv6 = true;
		socket.tcp = false;
		
		return socket;
	}
	
	/**
	 * Called to delegate the listener that responds to selector read events
	 * @param listener the new delegate listener
	 */
	public void setSocketListener(Listener listener)
	{
		delegate = listener;
	}
	
	/**
	 * Binds this socket to listen for incoming connections on the given port number, 
	 * without blocking {@code this} thread
	 * @param port the number of the port to open
	 * @throws IOException if an i/o error occurs
	 */
	public void bindToPort(int port) throws IOException
	{
		bindToPort(port, false);
	}
	
	/**
	 * Binds this socket to listen for incoming connections on the given port number. 
	 * @param port the number of the port to open
	 * @param blocking set to {@code true} to force {@code this} thread to block until a connection
	 * is received
	 * @throws IOException if an i/o error occurs
	 */
	public void bindToPort(int port, boolean blocking) throws IOException
	{
		selector = Selector.open();
		InetSocketAddress isa = null;
		if (ipv6)
		{
			InetAddress[] addresses = InetAddress.getAllByName("localhost");
			for (InetAddress addr : addresses)
			{
				if (!addr.isLoopbackAddress() && InetAddressUtils.isIPv6Address(addr.getHostAddress()))
				{
					isa = new InetSocketAddress(addr, port);
					break;
				}
			}
			if (isa == null)
				isa = new InetSocketAddress(port);//FIXME does this set an address? Should this method accept an address parameter?
		}
		else
			isa = new InetSocketAddress((InetAddress) null, port);
		address = isa.getAddress();
		
		if (tcp)
		{
			channel = SocketChannel.open();
			SocketChannel _channel = (SocketChannel) channel;
			_channel.socket().bind(isa);
			_channel.configureBlocking(blocking);
			this.port = _channel.socket().getLocalPort();
			
			readKey = _channel.register(selector, SelectionKey.OP_READ, delegate);
		}
		else
		{
			channel = DatagramChannel.open();
			DatagramChannel _channel = (DatagramChannel) channel;
			_channel.socket().bind(isa);
			_channel.configureBlocking(blocking);
			this.port = _channel.socket().getLocalPort();
			
			readKey = _channel.register(selector, SelectionKey.OP_READ, delegate);
			
		}

		selector.wakeup();
		
	}
	
	/**
	 * Connects this socket to the remote host at the given port number, 
	 * without blocking {@code this} thread
	 * @param _address the remote IP Address to which to connect.
	 * @param port the number of the port to open
	 * @throws SocketException if the given address is not IPv6 for a 
	 * IPv6 socket, or likewise for IPv4.
	 * @throws IOException if an i/o error occurs
	 */
	public void connectToAddress(InetAddress _address, int port) throws IOException
	{
		connectToAddress(_address, port, false);
	}
	
	/**
	 * Connects this socket to the remote host at the given port number.
	 * @param _address the remote IP Address to which to connect.
	 * @param port the number of the port to open
	 * @param blocking set to {@code true} to force {@code this} thread to block until a connection
	 * is received
	 * @throws SocketException if the given address is not IPv6 for a 
	 * IPv6 socket, or likewise for IPv4.
	 * @throws IOException if an i/o error occurs
	 */
	public void connectToAddress(InetAddress _address, int port, boolean blocking) throws IOException
	{
		selector = Selector.open();
		InetSocketAddress isa;
		
		if (ipv6)
		{
			if (InetAddressUtils.isIPv6Address(_address.getHostAddress()))
			{
				isa = new InetSocketAddress(_address, port);
			}
			else
			{
				throw new SocketException("Could not connect to IPv4 Address!");
			}
		}
		else
		{
			if (InetAddressUtils.isIPv4Address(_address.getHostAddress()))
			{
				isa = new InetSocketAddress(_address, port);
			}
			else
			{
				throw new SocketException("Could not connect to IPv6 Address!");
			}
		}
		
		address = isa.getAddress();
		
		if (tcp)
		{
			channel = SocketChannel.open();
			SocketChannel _channel = (SocketChannel) channel;
			_channel.socket().connect(isa);
			_channel.configureBlocking(blocking);
			this.port = _channel.socket().getLocalPort();
			
			writeKey = _channel.register(selector, SelectionKey.OP_WRITE, delegate);
		}
		else
		{
			channel = DatagramChannel.open();
			DatagramChannel _channel = (DatagramChannel) channel;
			_channel.socket().connect(isa);
			_channel.configureBlocking(blocking);
			this.port = _channel.socket().getLocalPort();

			writeKey = _channel.register(selector, SelectionKey.OP_WRITE, delegate);
			
		}
		
		selector.wakeup();
	}
	
	/**
	 * Enable broadcast datagrams. This must be enabled for a UDP socket to send or receive broadcasts.
	 * @throws IOException if the socket is closed or the option could not be set
	 * @throws NullPointerException if the socket is null. This will occur if {@link #bindToPort(int)}
	 * or {@link #connectToPort(int)} has not yet been called.
	 */
	public void enableBroadcast() throws IOException, NullPointerException
	{
		if (channel != null && channel instanceof DatagramChannel)
			((DatagramChannel) channel).socket().setBroadcast(true);
		else
			throw new NullPointerException("Socket has not been initialized! You must call bindToPort(int) or connectToPort(int) first.");
	}
	
	/**
	 * Stops the run loop at the next iteration by setting {@link #isLooping} to {@code false}.
	 */
	public void stop()
	{
		isLooping = false;
	}
	
	/**
	 * Retrieves the buffered input from the {@link #channel} and passes it to the {@link #delegate}
	 * listener.
	 * @param key the key that was selected
	 * @throws IOException if an i/o error occurs
	 */
	private void read(SelectionKey key) throws IOException {
		
		//clear the buffer so it is ready for new data
		this.channelBuffer.clear();
		
		if (tcp)
		{
			SocketChannel _channel = (SocketChannel) key.channel();

			// Attempt to read off the channel
			int numRead;
			try {
				numRead = _channel.read(this.channelBuffer);
			} catch (IOException e) {
				// The remote forcibly closed the connection, cancel
				// the selection key and close the channel.
				key.cancel();
				_channel.close();
				return;
			}

			if (numRead == -1) {
				// Remote entity shut the socket down cleanly. Do the
				// same from our end and cancel the channel.
				_channel.close();
				key.cancel();
				return;
			}
		}
		else
		{
			DatagramChannel _channel = (DatagramChannel) key.channel();

			// Attempt to read off the channel
			try {
				_channel.receive(this.channelBuffer);
			} catch (IOException e) {
				// The remote forcibly closed the connection, cancel
				// the selection key and close the channel.
				key.cancel();
				_channel.close();
				return;
			}
		}
		
		Listener listener = (Listener) key.attachment();
		if (listener != null)
			listener.onDataReceived(this, channelBuffer.array());
	}
	
	/**
	 * Sends the buffered output through the {@link #channel} and notifies the {@link #delegate} 
	 * listener of the event
	 * @param key the key that was selected
	 * @throws IOException if an i/o error occurs
	 */
	private void write(SelectionKey key) throws IOException 
	{

		// Clear out our read buffer so it's ready for new data
		this.channelBuffer.clear();
		
		if (tcp)
		{
			SocketChannel _channel = (SocketChannel) key.channel();

			// Attempt to send off the channel
			try {
				_channel.write(this.channelBuffer);
			} catch (IOException e) {
				// The remote forcibly closed the connection, cancel
				// the selection key and close the channel.
				key.cancel();
				_channel.close();
				return;
			}
		}
		else
		{
			DatagramChannel _channel = (DatagramChannel) key.channel();


			// Attempt to send off the channel
			try {
				_channel.write(this.channelBuffer);
			} catch (IOException e) {
				// The remote forcibly closed the connection, cancel
				// the selection key and close the channel.
				key.cancel();
				_channel.close();
				return;
			}
		}
		

		Listener listener = (Listener) key.attachment();
		if (listener != null)
			listener.onDataSent(this, channelBuffer.array());
	}

	/**
	 * Loops infinitely and handles input and output data by forwarding it to the delegate listener.
	 */
	public void run() 
	{
		isLooping = true;
		while(isLooping)
		{
			try
			{
				
				selector.select();
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iterator = keys.iterator();
				while (iterator.hasNext())
				{
					SelectionKey key = iterator.next();
					iterator.remove();
					
					if (key == this.readKey)
					{
						read(key);
					}
					else if (key == this.writeKey)
					{
						write(key);
					}
					
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		//cleanup
		try
		{
			selector.close();
			channel.close();
		}
		catch (IOException e)
		{
			//this is just to try to clean things up. If it doesn't work, then that is fine.
		}
		
	}


	
	/** @return the ip address of the open socket */
	public InetAddress getAddress()
	{
		return address;
	}
	
	/** @return the port of the socket */
	public int getPort()
	{
		return port;
	}
	
	/**
	 * Ensures that the run loop is exited upon destruction of this Object
	 */
	@Override
	public void finalize()
	{
		stop();
	}
	
	
	/**
	 * Listens for socket events and reacts to them via callback methods
	 * @author Phil Brown
	 *
	 */
	public interface Listener
	{
		/**
		 * Called when data is received by this socket
		 * @param server the socket that received the data
		 * @param data the data that was received
		 */
		public void onDataReceived(AsyncSocket server, byte[] data);
		
		/**
		 * Called when data is sent by this socket
		 * @param client the socket that sent the data
		 * @param data the data that was sent
		 */
		public void onDataSent(AsyncSocket client, byte[] data);
	}
}
