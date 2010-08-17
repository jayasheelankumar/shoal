/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

package org.shoal.ha.group.gms;

import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.*;
import com.sun.enterprise.ee.cms.logging.GMSLogDomain;
import com.sun.enterprise.ee.cms.spi.MemberStates;
import org.shoal.ha.cache.impl.util.MessageReceiver;
import org.shoal.ha.group.GroupMemberEventListener;
import org.shoal.ha.group.GroupService;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Mahesh Kannan
 */
public class GroupServiceProvider
        implements GroupService, CallBack {

    private static final Logger logger = GMSLogDomain.getLogger(GMSLogDomain.GMS_LOGGER);

    private String myName;

    private String groupName;

    private Properties configProps = new Properties();

    private GroupManagementService gms;

    private GroupHandle groupHandle;

    private ConcurrentHashMap<String, String> aliveInstances = new ConcurrentHashMap<String, String>();

    private List<GroupMemberEventListener> listeners = new ArrayList<GroupMemberEventListener>();

    private boolean createdAndJoinedGMSGroup;

    private AtomicLong previousViewId = new AtomicLong(-100);

    private volatile AliveAndReadyView arView;

    public GroupServiceProvider(String myName, String groupName, boolean startGMS) {
        init(myName, groupName, startGMS);
    }

    public void processNotification(Signal notification) {
        boolean isJoin = true;
        if ((notification instanceof JoinedAndReadyNotificationSignal)
            || (notification instanceof FailureNotificationSignal)
            || (notification instanceof PlannedShutdownSignal)) {

            isJoin = notification instanceof JoinedAndReadyNotificationSignal;

            checkAndNotifyAboutCurrentAndPreviousMembers(notification.getMemberToken(), isJoin);
        }
    }

    private synchronized void checkAndNotifyAboutCurrentAndPreviousMembers(String memberName, boolean isJoinEvent) {

        SortedSet<String> currentAliveAndReadyMembers = gms.getGroupHandle().getCurrentAliveAndReadyCoreView().getMembers();
        AliveAndReadyView aView = gms.getGroupHandle().getPreviousAliveAndReadyCoreView();
        SortedSet<String> previousAliveAndReadyMembers = new TreeSet<String>();

        if (aView == null) { //Possible during unit tests when listeners are registered before GMS is started
            return;
        }


        long arViewId = aView.getViewId();
        long knownId = previousViewId.get();
        Signal sig = aView.getSignal();

        System.out.println("**GroupServiceProvider:checkAndNotifyAboutCurrentAndPreviousMembers: previous viewID: " + knownId
                + "; current viewID: " + arViewId + "; " + aView.getSignal());
        if (knownId < arViewId) {
            if (previousViewId.compareAndSet(knownId, arViewId)) {
                this.arView = aView;
                sig = this.arView.getSignal();
                previousAliveAndReadyMembers = this.arView.getMembers();
            } else {
                previousAliveAndReadyMembers = this.arView.getMembers();
                System.out.println("**GroupServiceProvider:checkAndNotifyAboutCurrentAndPreviousMembers.  Entered ELSE 1");
            }
        } else {
            previousAliveAndReadyMembers = this.arView.getMembers();
            System.out.println("**GroupServiceProvider:checkAndNotifyAboutCurrentAndPreviousMembers.  Entered ELSE 2");
        }

        //Listeners must be notified even if view has not changed.
        //This is because this method is called when a listener
        //  is registered
        for (GroupMemberEventListener listener : listeners) {
            listener.onViewChange(memberName, currentAliveAndReadyMembers,
                    previousAliveAndReadyMembers, isJoinEvent);
        }

        StringBuilder sb = new StringBuilder("**VIEW: ");
        sb.append("prevViewId: " + knownId).append("; curViewID: ").append(arViewId)
                .append("; signal: ").append(sig).append(" ");
        sb.append("[current: ");
        String delim = "";
        for (String member : currentAliveAndReadyMembers) {
            sb.append(delim).append(member);
            delim = ", ";
        }
        sb.append("]  [previous: ");
        delim = "";

        for (String member : previousAliveAndReadyMembers) {
            sb.append(delim).append(member);
            delim = ", ";
        }
        sb.append("]");
        System.out.println(sb.toString());
        System.out.println("**********************************************************************");

    }

    private void init(String myName, String groupName, boolean startGMS) {
        try {
            gms = GMSFactory.getGMSModule(groupName);
        } catch (Exception e) {
            logger.severe("GMS module for group " + groupName + " not enabled");
        }

        if (gms == null) {
            if (startGMS) {
                logger.info("GroupServiceProvider *CREATING* gms module for group " + groupName);
                GroupManagementService.MemberType memberType = myName.startsWith("monitor-")
                        ? GroupManagementService.MemberType.SPECTATOR
                        : GroupManagementService.MemberType.CORE;

                configProps.put(ServiceProviderConfigurationKeys.MULTICASTADDRESS.toString(),
                        System.getProperty("MULTICASTADDRESS", "229.9.1.1"));
                configProps.put(ServiceProviderConfigurationKeys.MULTICASTPORT.toString(), 2299);
                logger.info("Is initial host=" + System.getProperty("IS_INITIAL_HOST"));
                configProps.put(ServiceProviderConfigurationKeys.IS_BOOTSTRAPPING_NODE.toString(),
                        System.getProperty("IS_INITIAL_HOST", "false"));
                if (System.getProperty("INITIAL_HOST_LIST") != null) {
                    configProps.put(ServiceProviderConfigurationKeys.VIRTUAL_MULTICAST_URI_LIST.toString(),
                            myName.equals("DAS"));
                }
                configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_RETRIES.toString(),
                        System.getProperty("MAX_MISSED_HEARTBEATS", "3"));
                configProps.put(ServiceProviderConfigurationKeys.FAILURE_DETECTION_TIMEOUT.toString(),
                        System.getProperty("HEARTBEAT_FREQUENCY", "2000"));
                //Uncomment this to receive loop back messages
                //configProps.put(ServiceProviderConfigurationKeys.LOOPBACK.toString(), "true");
                final String bindInterfaceAddress = System.getProperty("BIND_INTERFACE_ADDRESS");
                if (bindInterfaceAddress != null) {
                    configProps.put(ServiceProviderConfigurationKeys.BIND_INTERFACE_ADDRESS.toString(), bindInterfaceAddress);
                }

                gms = (GroupManagementService) GMSFactory.startGMSModule(
                        myName, groupName, memberType, configProps);

                createdAndJoinedGMSGroup = true;
            } else {
                logger.info("**GroupServiceProvider:: Will not start GMS module for group " + groupName + ". It should have been started by now. But GMS: " + gms);
            }
        } else {
            logger.info("**GroupServiceProvider:: GMS module for group " + groupName + " should have been started by now GMS: " + gms);
        }

        if (gms != null) {
            this.groupHandle = gms.getGroupHandle();
            this.myName = myName;
            this.groupName = groupName;

            gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
            gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
            gms.addActionFactory(new FailureNotificationActionFactoryImpl(this));
            gms.addActionFactory(new PlannedShutdownActionFactoryImpl(this));

            logger.info("**GroupServiceProvider:: REGISTERED member event listeners for <group, instance> => <" + groupName + ", " + myName + ">");

        } else {
            throw new IllegalStateException("GMS has not been started yet for group name: " + groupName);
        }

        if (createdAndJoinedGMSGroup) {
            try {
                gms.join();
                Thread.sleep(3000);
                gms.reportJoinedAndReadyState(groupName);
            } catch (Exception ex) {
                //TODO
            }
        }
    }

    public List<String> getCurrentCoreMembers() {
        return groupHandle.getCurrentCoreMembers();
    }

    public void shutdown() {
        //gms.shutdown();
    }

    @Override
    public String getGroupName() {
        return groupName;
    }

    @Override
    public String getMemberName() {
        return myName;
    }

    @Override
    public boolean sendMessage(String targetMemberName, String token, byte[] data) {
        try {
            groupHandle.sendMessage(targetMemberName, token, data);
            return true;
        } catch (GMSException gmsEx) {
            //TODO Log
        }

        return false;
    }

    @Override
    public void registerGroupMessageReceiver(String messageToken, MessageReceiver receiver) {
        logger.fine("[GroupServiceProvider]:  REGISTERED A MESSAGE LISTENER: "
                + receiver + "; for token: " + messageToken);
        gms.addActionFactory(new MessageActionFactoryImpl(receiver), messageToken);
    }

    @Override
    public void registerGroupMemberEventListener(GroupMemberEventListener listener) {
        listeners.add(listener);
        checkAndNotifyAboutCurrentAndPreviousMembers(myName, true);
    }

    @Override
    public void removeGroupMemberEventListener(GroupMemberEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void close() {
        if (createdAndJoinedGMSGroup) {
            shutdown();
        }

    }
}
