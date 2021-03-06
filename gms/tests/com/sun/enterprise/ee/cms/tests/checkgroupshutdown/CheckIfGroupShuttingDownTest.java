/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package com.sun.enterprise.ee.cms.tests.checkgroupshutdown;

import com.sun.enterprise.ee.cms.core.CallBack;
import com.sun.enterprise.ee.cms.core.GMSException;
import com.sun.enterprise.ee.cms.core.GroupManagementService;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sun.enterprise.ee.cms.core.*;
import com.sun.enterprise.ee.cms.impl.client.*;
import com.sun.enterprise.ee.cms.impl.common.GMSContextFactory;
import com.sun.enterprise.ee.cms.impl.base.Utility;

/**
 * Created by IntelliJ IDEA.
 * User: sheetal
 * Date: Jan 31, 2008
 * Time: 1:22:36 PM
 * This test is for making sure that the API added to check if the
 * group is shutting down works fine.
 * start the test as follows in 2 terminals :
 * "sh runcheckgroupshutdown.sh DAS" and "sh runcheckgroupshutdown.sh C1"
 * DAS will send out the announceShutdown() message which will be received by C1
 * C1 will print out the value for gms.isGroupBeingShutdown(group) before and after the message is received from DAS
 * This way the above API can be tested. The value returned should be false before DAS announces the GMSMessage
 * for shutdown and the nti should become true before C1 shuts down.
 */
public class CheckIfGroupShuttingDownTest implements CallBack{

    final static Logger logger = Logger.getLogger("CheckIfGroupShuttingDownTest");
    final Object waitLock = new Object();
    final String group = "Group";

    public static void main(String[] args) {
        Utility.setLogger(logger);
        Utility.setupLogHandler();
        CheckIfGroupShuttingDownTest check = new CheckIfGroupShuttingDownTest();
        String serverName = System.getProperty("TYPE");
        try {
            check.runSimpleSample(serverName);
        } catch (GMSException e) {
            logger.log(Level.SEVERE, "Exception occured while joining group:" + e);
        }
    }

    /**
     * Runs this sample
     * @throws GMSException
     */
    private void runSimpleSample(String serverName) throws GMSException {
        logger.log(Level.INFO, "Starting CheckIfGroupShuttingDownTest....");

        //initialize Group Management Service
        GroupManagementService gms = initializeGMS(serverName, group);

        //register for Group Events
        registerForGroupEvents(gms);
        //join group
        joinGMSGroup(group, gms);
        
        if (serverName.equals("C1"))
              logger.info("SHUTDOWN : Is the group shutting down ? : " + gms.isGroupBeingShutdown());

        try {
            waitForShutdown(10000);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, e.getMessage());
        }


        if (serverName.equals("DAS")) {
            GMSContextFactory.getGMSContext(group).announceGroupShutdown(group, GMSConstants.shutdownState.COMPLETED);
        }

        try {
            waitForShutdown(20000);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, e.getMessage());
        }
        
        if (serverName.equals("C1"))
            logger.info("SHUTDOWN : Now is the group shutting down ? : " + gms.isGroupBeingShutdown());

        leaveGroupAndShutdown(serverName, gms);


        if (serverName.equals("C1"))
            logger.info("After leaveGroupAndShutdown : Now is the group shutting down ? : " + gms.isGroupBeingShutdown());


        System.exit(0);
    }

    private GroupManagementService initializeGMS(String serverName, String groupName) {
         logger.log(Level.INFO, "Initializing Shoal for member: "+serverName+" group:"+groupName);
         return (GroupManagementService) GMSFactory.startGMSModule(serverName,
                 groupName, GroupManagementService.MemberType.CORE, null);
     }

     private void registerForGroupEvents(GroupManagementService gms) {
         logger.log(Level.INFO, "Registering for group event notifications");
         gms.addActionFactory(new JoinNotificationActionFactoryImpl(this));
         gms.addActionFactory(new FailureSuspectedActionFactoryImpl(this));
         gms.addActionFactory(new FailureNotificationActionFactoryImpl(this));
         gms.addActionFactory(new PlannedShutdownActionFactoryImpl(this));
         gms.addActionFactory(new JoinedAndReadyNotificationActionFactoryImpl(this));
     }

     private void joinGMSGroup(String groupName, GroupManagementService gms) throws GMSException {
         logger.log(Level.INFO, "Joining Group "+groupName);
         gms.join();
     }

        private void waitForShutdown(int time) throws InterruptedException {
        logger.log(Level.INFO, "waiting for " + time + " ms");
        synchronized(waitLock){
            waitLock.wait(time);
        }
    }

    private void leaveGroupAndShutdown(String serverName, GroupManagementService gms) {
        logger.log(Level.INFO, "Shutting down gms " + gms + "for server " + serverName);
        gms.shutdown(GMSConstants.shutdownType.GROUP_SHUTDOWN);
    }

    public void processNotification(Signal notification) {
        logger.info("calling processNotification()...");
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
