/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.mgmt.transport;

import com.sun.enterprise.ee.cms.logging.GMSLogDomain;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class that can be used by any calling code to do common routines about Network I/O
 *
 * @author Bongjae Chang
 */
public class NetworkUtility {

    private static final Logger LOG = GMSLogDomain.getLogger( GMSLogDomain.GMS_LOGGER );

    public static final String IPV4ANYADDRESS = "0.0.0.0";
    public static final String IPV6ANYADDRESS = "::";
    public static final String IPV4LOOPBACK = "127.0.0.1";
    public static final String IPV6LOOPBACK = "::1";

    /**
     * Constant which works as the IP "Any Address" value
     */
    public static final InetAddress ANYADDRESS;
    public static final InetAddress ANYADDRESSV4;
    public static final InetAddress ANYADDRESSV6;

    /**
     * Constant which works as the IP "Local Loopback" value;
     */
    public static final InetAddress LOOPBACK;
    public static final InetAddress LOOPBACKV4;
    public static final InetAddress LOOPBACKV6;

    public volatile static List<InetAddress> allLocalAddresses;
    public volatile static NetworkInterface firstNetworkInterface;
    public volatile static InetAddress firstInetAddressV4;
    public volatile static InetAddress firstInetAddressV6;
    public static AtomicBoolean preferIPv6Addresses = null;

    private static final boolean IS_AIX_JDK;

    static {
        boolean preferIPv6Addresses = getPreferIpv6Addresses();

        InetAddress GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName( IPV4ANYADDRESS );
        } catch( Exception ignored ) {
            if( LOG.isLoggable( Level.FINE ) )
                LOG.log( Level.FINE, "failed to intialize ANYADDRESSV4. Not fatal", ignored );
        }
        ANYADDRESSV4 = GET_ADDRESS;

        GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName( IPV6ANYADDRESS );
        } catch( Exception ignored ) {
            if( LOG.isLoggable( Level.FINE ) )
                LOG.log( Level.FINE, "failed to intialize IPV6ANYADDRESS. Not fatal", ignored );
        }
        ANYADDRESSV6 = GET_ADDRESS;

        ANYADDRESS = ( ANYADDRESSV4 == null || preferIPv6Addresses) ? ANYADDRESSV6 : ANYADDRESSV4;

        GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName( IPV4LOOPBACK );
        } catch( Exception ignored ) {
            if( LOG.isLoggable( Level.FINE ) )
                LOG.log( Level.FINE, "failed to intialize IPV4LOOPBACK. Not fatal", ignored );
        }
        LOOPBACKV4 = GET_ADDRESS;

        GET_ADDRESS = null;
        try {
            GET_ADDRESS = InetAddress.getByName( IPV6LOOPBACK );
        } catch( Exception ignored ) {
            if( LOG.isLoggable( Level.FINE ) )
                LOG.log( Level.FINE, "failed to intialize ANYADDRESSV4. Not fatal", ignored );
        }
        LOOPBACKV6 = GET_ADDRESS;

        LOOPBACK = ( LOOPBACKV4 == null || preferIPv6Addresses) ? LOOPBACKV6 : LOOPBACKV4;

        if( LOOPBACK == null || ANYADDRESS == null ) {
            throw new IllegalStateException( "failure initializing statics. Neither IPV4 nor IPV6 seem to work" );
        }
    }

    private static Method isLoopbackMethod = null;
    private static Method isUpMethod = null;
    private static Method supportsMulticastMethod = null;

    static {
        // JDK 1.6
        try {
            isLoopbackMethod = NetworkInterface.class.getMethod( "isLoopback" );
        } catch( Throwable t ) {
            isLoopbackMethod = null;
        }
        try {
            isUpMethod = NetworkInterface.class.getMethod( "isUp" );
        } catch( Throwable t ) {
            isUpMethod = null;
        }
        try {
            supportsMulticastMethod = NetworkInterface.class.getMethod( "supportsMulticast" );
        } catch( Throwable t ) {
            supportsMulticastMethod = null;
        }
        String vendor = System.getProperty("java.vendor");
        IS_AIX_JDK = vendor == null ? false  : vendor.startsWith("IBM");
    }

    /**
     * Returns all local addresses except for lookback and any local address
     * But, if any addresses were not found locally, the lookback is added to the list.
     *
     * @return List which contains available addresses locally
     */
    public static List<InetAddress> getAllLocalAddresses() {
        if( allLocalAddresses != null )
            return allLocalAddresses;
        List<InetAddress> allAddr = new ArrayList<InetAddress>();
        Enumeration<NetworkInterface> allInterfaces = null;
        try {
            allInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch( SocketException t ) {
            if( LOG.isLoggable( Level.INFO ) )
                LOG.log( Level.INFO, "Could not get local interfaces' list", t );
        }

        if( allInterfaces == null )
            allInterfaces = Collections.enumeration( Collections.<NetworkInterface>emptyList() );

        while( allInterfaces.hasMoreElements() ) {
            NetworkInterface anInterface = allInterfaces.nextElement();
            try {
                if (!isUp(anInterface)) {
                    continue;
                }
                Enumeration<InetAddress> allIntfAddr = anInterface.getInetAddresses();
                while( allIntfAddr.hasMoreElements() ) {
                    InetAddress anAddr = allIntfAddr.nextElement();
                    if( anAddr.isLoopbackAddress() || anAddr.isAnyLocalAddress() )
                        continue;
                    if( !allAddr.contains( anAddr ) ) {
                        allAddr.add( anAddr );
                    }
                }
            } catch( Throwable t ) {
                if( LOG.isLoggable( Level.INFO ) )
                    LOG.log( Level.INFO, "Could not get addresses for " + anInterface, t );
            }
        }

        if( allAddr.isEmpty() ) {
            if( LOOPBACKV4 != null )
                allAddr.add( LOOPBACKV4 );
            if( LOOPBACKV6 != null )
                allAddr.add( LOOPBACKV6 );
        }
        allLocalAddresses = allAddr;
        return allLocalAddresses;
    }

    public static InetAddress getAnyAddress() {
        if (getPreferIpv6Addresses() && ANYADDRESSV6 != null) {
            return ANYADDRESSV6;
        } else {
            return ANYADDRESSV4;
        }
    }

    public static InetAddress getLoopbackAddress() {
        if (getPreferIpv6Addresses() && LOOPBACKV6 != null) {
            return LOOPBACKV6;
        } else {
            return LOOPBACKV4;
        }
    }

    /**
     * Return a first network interface except for the lookback
     * But, if any network interfaces were not found locally, the lookback interface is returned.
     *
     * @return a first network interface
     * @throws IOException if an I/O error occurs or a network interface was not found
     */
    public static NetworkInterface getFirstNetworkInterface() throws IOException {
        if( firstNetworkInterface != null )
            return firstNetworkInterface;
        NetworkInterface loopback = null;
        NetworkInterface firstInterface = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while( interfaces != null && interfaces.hasMoreElements() ) {
            NetworkInterface anInterface = interfaces.nextElement();
            if( isLoopbackNetworkInterface( anInterface ) ) {
                loopback = anInterface;
                continue;
            }

            // removed check if multicast enabled.  Definitely not correct for non-multicast mode.
            if( getNetworkInetAddress(anInterface, false) != null ||
                getNetworkInetAddress(anInterface, true) != null ) {
                firstInterface = anInterface;
                break;
            }
        }
        if( firstInterface == null )
            firstInterface = loopback;
        if( firstInterface == null ) {
            throw new IOException( "failed to find a network interface" );
        } else {
            firstNetworkInterface = firstInterface;
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("getFirstNetworkInterface  result: interface name:" + firstNetworkInterface.getName() + " address:" + firstNetworkInterface.getInetAddresses().nextElement().toString());
            }
            return firstNetworkInterface;
        }
    }

    public static InetAddress getLocalHostAddress() {
        InetAddress result = null;
        try {
            result = InetAddress.getLocalHost();
        } catch (UnknownHostException ignore) {}
        return result;
    }

    public static boolean getPreferIpv6Addresses() {
        if (preferIPv6Addresses == null) {
            String propValue = null;
            boolean result = false;
            try {
                propValue = System.getProperty("java.net.preferIPv6Addresses", "false");
                result = Boolean.parseBoolean(propValue);
            } catch (Throwable t) {
                if (LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, "netutil.invalidPreferIPv6Addresses", new Object[]{t.getLocalizedMessage()});
                    LOG.log(Level.WARNING, "stack trace", t);
                }
            } finally {
                preferIPv6Addresses = new AtomicBoolean(result);
            }
        }
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "NetworkUtlity.getPreferIpv6Addresses=" + preferIPv6Addresses.get());
        }
        return preferIPv6Addresses.get();
    }

    /**
     * Return a first <code>InetAddress</code> of the first network interface
     * check java property java.net.preferIPv6Addresses for whether to favor IPv4 or IPv6.  (java default is to favor IPv4 addresses)
     * If unable to find a valid network interface, then fallback to trying to get localhost address as last resort.
     *
     * @return a first found <code>InetAddress</code>.
     * @throws IOException if an I/O error occurs or a network interface was not found
     */
    public static InetAddress getFirstInetAddress() throws IOException {            // check JDK defined property for whether to generate IPv4 or IPv6 addresses.  Default is ipv4.celtics
        boolean preferIPv6Addrs = getPreferIpv6Addresses();
        InetAddress firstInetAddress = NetworkUtility.getFirstInetAddress(preferIPv6Addrs);
        if (firstInetAddress == null) {
            firstInetAddress = NetworkUtility.getFirstInetAddress(!preferIPv6Addrs);
        }
        if (firstInetAddress == null) {

            // last ditch effort to get a valid public IP address.
            // just in case NetworkInterface methods such as isUp is working incorrectly on some platform,
            // Inspired by GLASSFISH-17195.
            firstInetAddress = NetworkUtility.getLocalHostAddress();

        }
        if (firstInetAddress == null) {
            throw new IOException("can not find a first InetAddress");
        } else {
            return firstInetAddress;
        }
    }

    /**
     * Return a first <code>InetAddress</code> of the first network interface
     * But, if any network interfaces were not found locally, <code>null</code> could be returned.
     *
     * @param preferIPv6 if true, prefer IPv6 InetAddress. otherwise prefer IPv4 InetAddress
     * @return a first found <code>InetAddress</code>.
     * @throws IOException if an I/O error occurs or a network interface was not found
     */
    public static InetAddress getFirstInetAddress( boolean preferIPv6 ) throws IOException {
//        LOG.info("enter getFirstInetAddress preferIPv6=" + preferIPv6);
        if( preferIPv6 && firstInetAddressV6 != null ) {
//            LOG.info("exit getFirstInetAddress cached ipv6 result=" + firstInetAddressV6);
            return firstInetAddressV6;
        }
        else if( !preferIPv6 && firstInetAddressV4 != null ) {
//            LOG.info("exit getFirstInetAddress cached ipv4 result=" + firstInetAddressV4);
            return firstInetAddressV4;
        }
        NetworkInterface anInterface = getFirstNetworkInterface();
//        LOG.info("getFirstInetAddress: first network interface=" + anInterface);
        if (anInterface == null) {
           if (preferIPv6 && firstInetAddressV6 != null ) {
               return firstInetAddressV6;
           } else {
               return firstInetAddressV4;
           }
        } else {
            return getNetworkInetAddress(anInterface, preferIPv6);
        }
    }

    /**
        * Return a first <code>InetAddress</code> of network interface
        * But, if any network interfaces were not found locally, <code>null</code> could be returned.
        *
        * @param preferIPv6 if true, prefer IPv6 InetAddress. otherwise prefer IPv4 InetAddress
        * @return a first found <code>InetAddress</code>.
        * @throws IOException if an I/O error occurs or a network interface was not found
        */
    public static InetAddress getNetworkInetAddress(NetworkInterface anInterface, boolean preferIPv6) throws IOException {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("enter getNetworkInetAddress networkInterface=" + anInterface + " preferIPv6=" + preferIPv6);
        }
        if (anInterface == null) {
            return null;
        }
        InetAddress firstInetAddressV6 = null;
        InetAddress firstInetAddressV4 = null;

        Enumeration<InetAddress> allIntfAddr = anInterface.getInetAddresses();
        while (allIntfAddr.hasMoreElements()) {
            InetAddress anAddr = allIntfAddr.nextElement();
//            LOG.info("getNetworkInetAddress: anAddr=" + anAddr);
            // allow loopback address.  only work on a single machine. used for development.
            //if( anAddr.isLoopbackAddress() || anAddr.isAnyLocalAddress() )
            //    continue;
            if (firstInetAddressV6 == null && anAddr instanceof Inet6Address)
                firstInetAddressV6 = anAddr;
            else if (firstInetAddressV4 == null && anAddr instanceof Inet4Address)
                firstInetAddressV4 = anAddr;
            if (firstInetAddressV6 != null && firstInetAddressV4 != null)
                break;
        }
        if (preferIPv6 && firstInetAddressV6 != null) {
//            LOG.info("exit getNetworkInetAddress ipv6 result=" + firstInetAddressV6);
            return firstInetAddressV6;
        } else {
//            LOG.info("exit getNetworkInetAddress ipv4 result=" + firstInetAddressV4);
            return firstInetAddressV4;
        }
    }



    public static boolean isLoopbackNetworkInterface( NetworkInterface anInterface ) {
        if( anInterface == null )
            return false;
        if( isLoopbackMethod != null ) {
            try {
                return (Boolean)isLoopbackMethod.invoke( anInterface );
            } catch( Throwable t ) {
            }
        }
        boolean hasLoopback = false;
        Enumeration<InetAddress> allIntfAddr = anInterface.getInetAddresses();
        while( allIntfAddr.hasMoreElements() ) {
            InetAddress anAddr = allIntfAddr.nextElement();
            if( anAddr.isLoopbackAddress() ) {
                hasLoopback = true;
                break;
            }
        }
        return hasLoopback;
    }

    public static boolean supportsMulticast( NetworkInterface anInterface ) {
        if( anInterface == null )
            return false;
        boolean result = true;
        if( isUpMethod != null ) {
            try {
                result = (Boolean)isUpMethod.invoke( anInterface );
            } catch( Throwable t ) {
                result = false;
            }
        }
        if (!result) {
            return result;
        } else if (IS_AIX_JDK) {

            // workaround for Network.supportsMulticast not working properly on AIX.
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Workaround for java.net.NetworkInterface.supportsMulticast() returning false on AIX");
            }
            return true;
        } else if( supportsMulticastMethod != null) {
            try {
                return (Boolean)supportsMulticastMethod.invoke( anInterface );
            } catch( Throwable t ) {
                // will just return false in this case
            }
        }

        return false;
    }

    public static boolean isUp( NetworkInterface anInterface ) {
        if( anInterface == null )
            return false;
        if( isUpMethod != null ) {
            try {
                return (Boolean)isUpMethod.invoke( anInterface );
            } catch( Throwable t ) {
                // will just return false in this case
            }
        }
        return false;
    }

    public static void writeIntToByteArray( final byte[] bytes, final int offset, final int value ) throws IllegalArgumentException {
        if( bytes == null )
            return;
        if( bytes.length < offset + 4 )
            throw new IllegalArgumentException( "bytes' length is too small" );
        bytes[offset + 0] = (byte)( ( value >> 24 ) & 0xFF );
        bytes[offset + 1] = (byte)( ( value >> 16 ) & 0xFF );
        bytes[offset + 2] = (byte)( ( value >> 8 ) & 0xFF );
        bytes[offset + 3] = (byte)( value & 0xFF );
    }

    public static int getIntFromByteArray( final byte[] bytes, final int offset ) throws IllegalArgumentException {
        if( bytes == null )
            return 0;
        if( bytes.length < offset + 4 )
            throw new IllegalArgumentException( "bytes' length is too small" );
        int ch1 = bytes[offset] & 0xff;
        int ch2 = bytes[offset + 1] & 0xff;
        int ch3 = bytes[offset + 2] & 0xff;
        int ch4 = bytes[offset + 3] & 0xff;
        return (int)( ( ch1 << 24 ) + ( ch2 << 16 ) + ( ch3 << 8 ) + ch4 );
    }

    public static int serialize( final OutputStream baos, final Map<String, Serializable> messages ) throws MessageIOException {
        return serialize( baos, messages, false);
    }

    public static int serialize( final OutputStream baos, final Map<String, Serializable> messages, final boolean debug ) throws MessageIOException {
        int count = 0;
        if( baos == null || messages == null )
            return count;
        String name = null;
        ObjectOutputStream oos = null;
        try {
            if( debug ) {
                oos = new DebuggingObjectOutputStream( baos );
            } else {
                oos = new ObjectOutputStream( baos );
            }
            for( Map.Entry<String, Serializable> entry : messages.entrySet() ) {
                name = entry.getKey();
                Serializable obj = entry.getValue();
                count++;
                oos.writeObject( name );
                oos.writeObject( obj );
            }
            oos.flush();
        } catch( Throwable t ) {
            throw new MessageIOException( "failed to serialize a message : name = " + name + "." +
                                          ( debug ? " path to bad object: " + ( (DebuggingObjectOutputStream)oos ).getStack() : "" ),
                                          t );
        } finally {
            if( oos != null ) {
                try {
                    oos.close();
                } catch( IOException e ) {
                }
            }
        }
        return count;
    }

    public static void deserialize( final InputStream is, final int count, final Map<String, Serializable> messages ) throws MessageIOException {
        if( is == null || count <= 0 || messages == null )
            return;
        String name = null;
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream( is );
            Object obj = null;
            for( int i = 0; i < count; i++ ) {
                name = (String)ois.readObject();
                obj = ois.readObject();
                if( obj instanceof Serializable ) {
                    messages.put( name, (Serializable)obj );
                }
            }
        } catch( Throwable t ) {
            LOG.log(Level.WARNING,
                    "netutil.deserialize.failure", new Object[]{messages.toString(), name, Thread.currentThread().getName()});
            throw new MessageIOException( "failed to deserialize a message : name = " + name, t );
        } finally {
            if( ois != null ) {
                try {
                    ois.close();
                } catch( IOException e ) {
                }
            }
        }
    }

    /**
     * Returns an available tcp port between <code>tcpStartPort</code> and <code>tcpEndPort</code>
     *
     * @param host specific host name
     * @param tcpStartPort start port
     * @param tcpEndPort end port
     * @return an available tcp port which is not bound yet. Throws IllegalStateException if no ports exist.
     */
    /*
    // Using grizzly tcp port selection from a range.
    public static int getAvailableTCPPort( String host, int tcpStartPort, int tcpEndPort ) {
        if( tcpStartPort > tcpEndPort )
            tcpEndPort = tcpStartPort + 30;
        for( int portInRange = tcpStartPort; portInRange <= tcpEndPort; portInRange++ ) {
            ServerSocket testSocket = null;
            try {
                testSocket = new ServerSocket( portInRange, -1, host == null ? null : InetAddress.getByName( host ) );
            } catch( IOException ie ) {
                continue;
            } finally {
                if( testSocket != null ) {
                    try {
                        testSocket.close();
                    } catch( IOException e ) {
                    }
                }
            }
            return portInRange;
        }
        LOG.log(Level.SEVERE, "netutil.no.available.ports", new Object[]{host,tcpStartPort,tcpEndPort});
        throw new IllegalStateException("Fatal error. No available ports exist for " + host + " in range " + tcpStartPort + " to " + tcpEndPort);
    }
    */

    private static final Field DEPTH_FIELD;

    static {
        try {
            DEPTH_FIELD = ObjectOutputStream.class.getDeclaredField( "depth" );
            DEPTH_FIELD.setAccessible( true );
        } catch( NoSuchFieldException e ) {
            throw new AssertionError( e );
        }
    }

    /**
     * This class extends <code>ObjectOutputStream</code> for providing any debugging informations when an object is written
     */
    private static class DebuggingObjectOutputStream extends ObjectOutputStream {

        final List<Object> stack = new ArrayList<Object>();
        boolean broken = false;

        public DebuggingObjectOutputStream( OutputStream out ) throws IOException {
            super( out );
            enableReplaceObject( true );
        }

        protected Object replaceObject( Object o ) {
            int currentDepth = currentDepth();
            if( o instanceof IOException && currentDepth == 0 )
                broken = true;
            if( !broken ) {
                truncate( currentDepth );
                stack.add( o );
            }
            return o;
        }

        private void truncate( int depth ) {
            while( stack.size() > depth ) {
                pop();
            }
        }

        private Object pop() {
            return stack.remove( stack.size() - 1 );
        }

        private int currentDepth() {
            try {
                Integer oneBased = (Integer)DEPTH_FIELD.get( this );
                return oneBased - 1;
            } catch( IllegalAccessException e ) {
                throw new AssertionError( e );
            }
        }

        public List<Object> getStack() {
            return stack;
        }
    }

    public static boolean isBindAddressValid(String addressString) {
        ServerSocket socket = null;
        try {
            InetAddress ia = null;
            try {
                ia = Inet4Address.getByName(addressString);
            } catch (Exception e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, "in isBindAddressValid(" + addressString + "): Inet4Address.getByName(" +
                            addressString + ")", e);
                } else {
                    LOG.log(Level.INFO, "in isBindAddressValid(" + addressString + "): Inet4Address.getByName(" +
                            addressString + ") handled exception " + e.getClass().getSimpleName());
                }
            }

            if (ia == null) {
                // check if address string is a network interface.
               NetworkInterface netInt = NetworkInterface.getByName(addressString);
               ia = getNetworkInetAddress(netInt, false);
            }

            // calling ServerSocket with null for ia means to use any local address that is available.
            // thus, if ia is not non-null at this point, must return false here.
            if (ia == null) {
                return false;
            }

            // port 0 means any free port
            // backlog 0 means use default
            socket = new ServerSocket(0, 0, ia);

            // make extra sure
            boolean retVal = socket.isBound();
            if (!retVal) {
                LOG.log(Level.WARNING, "netutil.validate.bind.not.bound", addressString);
            }
            return retVal;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "netutil.validate.bind.address.exception",new Object[]{addressString, e.toString()});
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ioe) {
                    LOG.log(Level.FINE,
                        "Could not close socket used to validate address.");
                }
            }
        }
    }

    public static InetAddress resolveBindInterfaceName(String addressString) {
        InetAddress ia = null;
        try {
            try {
                ia = Inet4Address.getByName(addressString);
            } catch (Exception e) {
            }
            if (ia == null) {
                try {
                    ia = Inet6Address.getByName(addressString);
                } catch (Exception e) {
                }

                // if ia is still null, check if address string is a network interface name.
                if (ia == null) {
                    NetworkInterface netInt = NetworkInterface.getByName(addressString);
                    if (netInt != null) {
                        try {
                            ia = getNetworkInetAddress(netInt, false);  // get IPv4
                        } catch (IOException ioe) {
                        }
                        if (ia == null) {
                            try {
                                ia = getNetworkInetAddress(netInt, true);  // get IPv6
                            } catch (IOException ioe) {
                            }
                        }
                    }
                }
            }
            return ia;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "resolveBindInterfaceName", e);
            LOG.log(Level.WARNING, "netutil.validate.bind.address.exception",
                    new Object[]{addressString, e.toString()});
            return null;
        }
    }

    public static void main( String[] args ) throws IOException {
        final String preferIPv6PropertyValue =  System.getProperty("java.net.preferIPv6Addresses", "false");
        System.out.println("Java property java.net.preferIPv6Addresses=" + preferIPv6PropertyValue);
        boolean preferIPv6Addrs = Boolean.parseBoolean(preferIPv6PropertyValue);
        System.out.println( "AllLocalAddresses() = " + getAllLocalAddresses() );
        System.out.println( "getFirstNetworkInterface() = " +getFirstNetworkInterface() );
        System.out.println( "getFirstInetAddress(preferIPv6Addresses:" + preferIPv6Addrs + ")=" + getFirstInetAddress(preferIPv6Addrs));
        System.out.println( "getFirstInetAddress()=" + getFirstInetAddress());
        System.out.println( "getFirstInetAddress( true ) = " + getFirstInetAddress( true ) );
        InetAddress ia = getFirstInetAddress(false);
        System.out.println( "getFirstInetAddress( false ) = " + getFirstInetAddress( false ) );
        System.out.println("getLocalHostAddress = " + getLocalHostAddress());
        System.out.println( "getFirstNetworkInteface() = " + NetworkUtility.getFirstNetworkInterface());
        System.out.println( "getNetworkInetAddress(firstNetworkInteface, true) = " +
               NetworkUtility.getNetworkInetAddress(NetworkUtility.getFirstNetworkInterface(), true));
        System.out.println( "getNetworkInetAddress(firstNetworkInteface, false) = " +
               NetworkUtility.getNetworkInetAddress(NetworkUtility.getFirstNetworkInterface(), false));
               Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        System.out.println("\n-------------------------------------------------------");
        System.out.println("\nAll Network Interfaces");
        for (NetworkInterface netint : Collections.list(nets)) {
            System.out.println("\n\n**************************************************");
            displayInterfaceInformation(netint);
        }
    }

    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        System.out.printf("Display name: %s\n", netint.getDisplayName());
        System.out.printf("Name: %s\n", netint.getName());
        System.out.printf("PreferIPv6Addresses: %b\n", getPreferIpv6Addresses());

        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            System.out.printf("InetAddress: %s\n", inetAddress);
        }

        System.out.printf("Up? %s\n", netint.isUp());
        System.out.printf("Loopback? %s\n", netint.isLoopback());
        System.out.printf("PointToPoint? %s\n", netint.isPointToPoint());
        System.out.printf("Supports multicast? %s\n", netint.supportsMulticast());
        System.out.printf("Virtual? %s\n", netint.isVirtual());
        System.out.printf("Hardware address: %s\n",
                    java.util.Arrays.toString(netint.getHardwareAddress()));
        System.out.printf("MTU: %s\n", netint.getMTU());
        try {
            System.out.printf("Network Inet Address (preferIPV6=false) %s\n", getNetworkInetAddress(netint, false).toString() );
        } catch (IOException ignore) {}
        try {
            System.out.printf("Network Inet Address (preferIPV6=true) %s\n", getNetworkInetAddress(netint, true).toString());
        } catch (IOException ignore) {}
        InetAddress ia = resolveBindInterfaceName(netint.getName());
        String ia_string = ia != null ? ia.getHostAddress() : "<unresolved>";
        System.out.printf("resolveBindInterfaceName(%s)=%s", netint.getName(), ia_string);

        System.out.printf("\n");
    }
}
