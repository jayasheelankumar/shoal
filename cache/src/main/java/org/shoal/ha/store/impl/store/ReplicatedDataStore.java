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

package org.shoal.ha.store.impl.store;

import org.shoal.ha.group.GroupService;
import org.shoal.ha.mapper.KeyMapper;
import org.shoal.ha.store.api.*;
import org.shoal.ha.store.impl.command.*;
import org.shoal.ha.store.impl.util.StringKeyMapper;

import java.util.Collection;

/**
 * @author Mahesh Kannan
 */
public class ReplicatedDataStore<K, V>
        implements DataStore<K, V> {

    private String storeName;

    private String instanceName;

    private String groupName;

    private GroupService gs;

    private CommandManager<K, V> cm;

    private DataStoreEntryHelper<K, V> transformer;

    public ReplicatedDataStore(String storeName, GroupService gs) {
        this(storeName, gs,
                new DefaultDataStoreEntryHelper<K, V>(Thread.currentThread().getContextClassLoader()),
                new StringKeyMapper<K>(gs.getGroupName()));
    }

    public ReplicatedDataStore(String storeName, GroupService gs,
                               DataStoreEntryHelper<K, V> helper) {
        this(storeName, gs, helper,
                new StringKeyMapper<K>(gs.getGroupName()));
    }

    public ReplicatedDataStore(String storeName, GroupService gs,
                               DataStoreEntryHelper<K, V> helper, KeyMapper<K> keyMapper) {
        this.storeName = storeName;
        this.gs = gs;
        this.instanceName = gs.getMemberName();
        this.groupName = gs.getGroupName();

        DataStoreContext<K, V> dsc = new DataStoreContext<K, V>(storeName, gs);
        this.transformer = helper;
        dsc.setDataStoreEntryHelper(helper);
        cm = new CommandManager<K, V>(dsc);
    }

    @Override
    public void put(K k, V v) {
        SaveCommand<K, V> cmd = new SaveCommand<K, V>();
        cmd.setValue(v);
        cm.execute(cmd);
    }

    @Override
    public void updateDelta(K k, Object obj) {
        UpdateDeltaCommand<K, V> cmd = new UpdateDeltaCommand<K, V>();
        cmd.setObject(obj);
        cm.execute(cmd);
    }

    @Override
    public V get(K k) {
        LoadRequestCommand<K, V> cmd = new LoadRequestCommand<K, V>(k);
        cm.execute(cmd);

        try {
            return transformer.getV(cmd.getReplicationEntry());
        } catch (DataStoreException dsEx) {
            //TODO Log?
            return null;
        }
    }

    @Override
    public void remove(K k) {
        RemoveCommand<K, V> cmd = new RemoveCommand<K, V>();
        cmd.setKey(k);
        cm.execute(cmd);
    }

    @Override
    public void touch(K k, long timestamp) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void removeIdleEntries(long idleFor) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Collection find(DataStoreEntryEvaluator<K, V> kvDataStoreEntryEvaluator) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void update(DataStoreEntryEvaluator<K, V> kvDataStoreEntryEvaluator) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void close() {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
