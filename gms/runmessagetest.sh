#!/bin/sh 

#
# Copyright 2004-2005 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.
#
 #
 # The contents of this file are subject to the terms
 # of the Common Development and Distribution License
 # (the License).  You may not use this file except in
 # compliance with the License.
 #
 # You can obtain a copy of the license at
 # https://shoal.dev.java.net/public/CDDLv1.0.html
 #
 # See the License for the specific language governing
 # permissions and limitations under the License.
 #
 # When distributing Covered Code, include this CDDL
 # Header Notice in each file and include the License file
 # at
 # If applicable, add the following below the CDDL Header,
 # with the fields enclosed by brackets [] replaced by
 # you own identifying information:
 # "Portions Copyrighted [year] [name of copyright owner]"
 #
 # Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 #
publish_home=./dist
lib_home=./lib

usage () {
    cat << USAGE 
Usage: $0 <parameters...> 
The required parameters are :
 <instance_id_token> <sendto-instance_id_token> <sendingThreadNumber> <log level> <tcpstartport> <tcpendport>
<tcpstartport> and <tcpendport> are optional.  Grizzly and jxta transports have different defaults.
USAGE
   exit 0
}
java -Dcom.sun.management.jmxremote -DLOG_LEVEL=$4 -cp ${publish_home}/shoal-gms-tests.jar:${publish_home}/shoal-gms.jar:${lib_home}/bcprov-jdk14.jar:${lib_home}/grizzly-framework.jar:${lib_home}/grizzly-utils.jar -DTCPSTARTPORT=$5 -DTCPENDPORT=$6 -DSHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly com.sun.enterprise.shoal.multithreadmessagesendertest.MultiThreadMessageSender $1 $2 $3 \;
