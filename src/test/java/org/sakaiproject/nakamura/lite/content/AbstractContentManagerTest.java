/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.lite.content;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sakaiproject.nakamura.api.lite.CacheHolder;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Configuration;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.PrincipalValidatorResolver;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;
import org.sakaiproject.nakamura.lite.ConfigurationImpl;
import org.sakaiproject.nakamura.lite.LoggingStorageListener;
import org.sakaiproject.nakamura.lite.RepositoryImpl;
import org.sakaiproject.nakamura.lite.accesscontrol.AccessControlManagerImpl;
import org.sakaiproject.nakamura.lite.accesscontrol.AuthenticatorImpl;
import org.sakaiproject.nakamura.lite.accesscontrol.PrincipalValidatorResolverImpl;
import org.sakaiproject.nakamura.lite.authorizable.AuthorizableActivator;
import org.sakaiproject.nakamura.lite.storage.ConcurrentLRUMap;
import org.sakaiproject.nakamura.lite.storage.StorageClient;
import org.sakaiproject.nakamura.lite.storage.StorageClientPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public abstract class AbstractContentManagerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContentManagerTest.class);
    private StorageClient client;
    private ConfigurationImpl configuration;
    private StorageClientPool clientPool;
    private Map<String, CacheHolder> sharedCache = new ConcurrentLRUMap<String, CacheHolder>(1000);
    private PrincipalValidatorResolver principalValidatorResolver = new PrincipalValidatorResolverImpl();

    @Before
    public void before() throws StorageClientException, AccessDeniedException, ClientPoolException,
            ClassNotFoundException, IOException {

        Map<String, Object> properties = Maps.newHashMap();
        properties.put("keyspace", "n");
        properties.put("acl-column-family", "ac");
        properties.put("authorizable-column-family", "au");
        properties.put("content-column-family", "cn");
        configuration = new ConfigurationImpl();
        configuration.activate(properties);
        clientPool = getClientPool(configuration);
        client = clientPool.getClient();
        AuthorizableActivator authorizableActivator = new AuthorizableActivator(client,
                configuration);
        authorizableActivator.setup();
        LOGGER.info("Setup Complete");
    }

    protected abstract StorageClientPool getClientPool(Configuration configuration) throws ClassNotFoundException;

    @After
    public void after() throws ClientPoolException {
        client.close();
    }

    @Test
    public void testCreateContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, null,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration, null,  new LoggingStorageListener());
        contentManager.update(new Content("/testCreateContent", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/testCreateContent/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/testCreateContent/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/testCreateContent");
        Assert.assertEquals("/testCreateContent", content.getPath());
        Map<String, Object> p = content.getProperties();
        LOGGER.info("Properties is {}",p);
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testCreateContent/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testCreateContent/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

    }

    @Test
    public void testCreateContent2() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, null,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration, null,  new LoggingStorageListener());
        contentManager.update(new Content("newRootTestCreateContent", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("newRootTestCreateContent/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("newRootTestCreateContent/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("newRootTestCreateContent");
        Assert.assertEquals("newRootTestCreateContent", content.getPath());
        Map<String, Object> p = content.getProperties();
        LOGGER.info("Properties is {}",p);
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("newRootTestCreateContent/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("newRootTestCreateContent/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

    }

    @Test
    public void testConentTree() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, null,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration, null, new LoggingStorageListener());
        contentManager.update(new Content("testConentTree/1/11/111", ImmutableMap.of("prop111",
                (Object) "value111")));
        contentManager.update(new Content("testConentTree/1/11/333", ImmutableMap.of("prop333",
                (Object) "value333")));
        contentManager.update(new Content("testConentTree/1/11/222", ImmutableMap.of("prop222",
                (Object) "value222")));

        contentManager.update(new Content("testConentTree/1/22/444", ImmutableMap.of("prop444",
                (Object) "value444")));
        contentManager.update(new Content("testConentTree/1/22/555", ImmutableMap.of("prop555",
                (Object) "value555")));
        contentManager.update(new Content("testConentTree/1/22/666", ImmutableMap.of("prop666",
                (Object) "value666")));
        contentManager.update(new Content("testConentTree/1/22/777", ImmutableMap.of("prop777",
                (Object) "value777")));
        Content content11 = contentManager.get("testConentTree/1/11");
        Set<String> childSet = Sets.newHashSet();
        int i = 0;
        for ( String c : content11.listChildPaths()){
            i++;
            childSet.add(c);
        }
        Assert.assertEquals(i,childSet.size());
        Assert.assertEquals(i,3);
        Assert.assertTrue(childSet.contains("testConentTree/1/11/111"));
        Assert.assertTrue(childSet.contains("testConentTree/1/11/222"));
        Assert.assertTrue(childSet.contains("testConentTree/1/11/333"));

        content11 = contentManager.get("testConentTree/1/22");
        childSet.clear();
        i = 0;
        for ( String c : content11.listChildPaths()){
            i++;
            childSet.add(c);
        }
        Assert.assertEquals(i,childSet.size());
        Assert.assertEquals(i,4);
        Assert.assertTrue(childSet.contains("testConentTree/1/22/444"));
        Assert.assertTrue(childSet.contains("testConentTree/1/22/555"));
        Assert.assertTrue(childSet.contains("testConentTree/1/22/666"));
        Assert.assertTrue(childSet.contains("testConentTree/1/22/777"));

        content11 = contentManager.get("testConentTree/1");
        childSet.clear();
        i = 0;
        for ( String c : content11.listChildPaths()){
            i++;
            childSet.add(c);
        }
        Assert.assertEquals(i,childSet.size());
        Assert.assertEquals(i,2);
        Assert.assertTrue(childSet.contains("testConentTree/1/11"));
        Assert.assertTrue(childSet.contains("testConentTree/1/22"));


    }


    @Test
    public void testCopySimple() throws StorageClientException, AccessDeniedException, IOException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, null,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration, null, new LoggingStorageListener());
        contentManager.update(new Content("testCopySimple/source/thefile", ImmutableMap.of("prop",
                (Object) "source")));
        contentManager.update(new Content("testCopySimple/destination", ImmutableMap.of("prop",
                (Object) "dest")));

        contentManager.copy("testCopySimple/source/thefile", "testCopySimple/destination/target", false);
        Content check = contentManager.get("testCopySimple/source/thefile");
        Assert.assertEquals("testCopySimple/source/thefile", check.getPath());
        Assert.assertEquals("source", check.getProperty("prop"));
        Set<String> checkChildren = Sets.newHashSet();
        int countChildren = 0;
        for ( String child : check.listChildPaths()) {
            countChildren++;
            checkChildren.add(child);
        }
        Assert.assertEquals(0, countChildren);
        Assert.assertEquals(0, checkChildren.size());

        check = contentManager.get("testCopySimple/destination");
        Assert.assertEquals("testCopySimple/destination", check.getPath());
        Assert.assertEquals("dest", check.getProperty("prop"));

        checkChildren = Sets.newHashSet();
        countChildren = 0;
        for ( String child : check.listChildPaths()) {
            countChildren++;
            checkChildren.add(child);
        }
        Assert.assertEquals(1, countChildren);
        Assert.assertEquals(1, checkChildren.size());
        Assert.assertTrue(checkChildren.contains("testCopySimple/destination/target"));

        check = contentManager.get("testCopySimple/destination/target");
        Assert.assertNotNull(check);
        Assert.assertEquals("testCopySimple/destination/target", check.getPath());
        Assert.assertEquals("source", check.getProperty("prop"));

        checkChildren = Sets.newHashSet();
        countChildren = 0;
        for ( String child : check.listChildPaths()) {
            countChildren++;
            checkChildren.add(child);
        }
        Assert.assertEquals(0, countChildren);
        Assert.assertEquals(0, checkChildren.size());
    }

    @Test
    public void testCopyOverwrite() throws StorageClientException, AccessDeniedException, IOException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, null,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration, null, new LoggingStorageListener());
        contentManager.update(new Content("testCopyOverwrite/source/thefile", ImmutableMap.of("prop",
                (Object) "source")));
        contentManager.update(new Content("testCopyOverwrite/destination/target", ImmutableMap.of("prop",
                (Object) "dest")));

        contentManager.copy("testCopyOverwrite/source/thefile", "testCopyOverwrite/destination/target", false);
        Content check = contentManager.get("testCopyOverwrite/source/thefile");
        Assert.assertEquals("testCopyOverwrite/source/thefile", check.getPath());
        Assert.assertEquals("source", check.getProperty("prop"));
        Set<String> checkChildren = Sets.newHashSet();
        int countChildren = 0;
        for ( String child : check.listChildPaths()) {
            countChildren++;
            checkChildren.add(child);
        }
        Assert.assertEquals(0, countChildren);
        Assert.assertEquals(0, checkChildren.size());

        check = contentManager.get("testCopyOverwrite/destination");
        Assert.assertEquals("testCopyOverwrite/destination", check.getPath());
        Assert.assertNull(check.getProperty("prop"));

        checkChildren = Sets.newHashSet();
        countChildren = 0;
        for ( String child : check.listChildPaths()) {
            countChildren++;
            checkChildren.add(child);
        }
        Assert.assertEquals(1, countChildren);
        Assert.assertEquals(1, checkChildren.size());
        Assert.assertTrue(checkChildren.contains("testCopyOverwrite/destination/target"));

        check = contentManager.get("testCopyOverwrite/destination/target");
        Assert.assertNotNull(check);
        Assert.assertEquals("testCopyOverwrite/destination/target", check.getPath());
        Assert.assertEquals("source", check.getProperty("prop"));

        checkChildren = Sets.newHashSet();
        countChildren = 0;
        for ( String child : check.listChildPaths()) {
            countChildren++;
            checkChildren.add(child);
        }
        Assert.assertEquals(0, countChildren);
        Assert.assertEquals(0, checkChildren.size());
    }



    @Test
    public void testSimpleDelete() throws AccessDeniedException, StorageClientException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        String path = "/testSimpleDelete/test2/test3/test4";
        String parentPath = "/testSimpleDelete/test2/test3";
        contentManager.update(new Content(parentPath, ImmutableMap.of("propParent", (Object) "valueParent")));
        contentManager.update(new Content(path, ImmutableMap.of("prop1", (Object) "value1")));
        Content content = contentManager.get(path);
        Assert.assertNotNull(content);
        Assert.assertEquals("value1", content.getProperty("prop1"));

        contentManager.delete(path);
        Assert.assertNull(contentManager.get(path));
        content = contentManager.get(parentPath);
        Assert.assertNotNull(content);
        Assert.assertEquals("valueParent", content.getProperty("propParent"));

        contentManager.delete("/testSimpleDelete", true);

    }

    @Test
    public void testSimpleDeleteRoot() throws AccessDeniedException, StorageClientException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        String path = "testSimpleDeleteRoot/test2/test3/test4";
        String parentPath = "testSimpleDeleteRoot/test2/test3";
        contentManager.update(new Content(parentPath, ImmutableMap.of("propParent", (Object) "valueParent")));
        contentManager.update(new Content(path, ImmutableMap.of("prop1", (Object) "value1")));
        Content content = contentManager.get(path);
        Assert.assertNotNull(content);
        Assert.assertEquals("value1", content.getProperty("prop1"));

        contentManager.delete(path);
        Assert.assertNull(contentManager.get(path));
        content = contentManager.get(parentPath);
        Assert.assertNotNull(content);
        Assert.assertEquals("valueParent", content.getProperty("propParent"));

        contentManager.delete("testSimpleDeleteRoot", true);

    }

    @Test
    public void testDeleteContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        contentManager.update(new Content("/testDeleteContent", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/testDeleteContent/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/testDeleteContent/test/ing", ImmutableMap.of("prop1", (Object) "value3")));
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 5; k++) {
                    contentManager.update(new Content("/testDeleteContent/test/ing/" + i + "/" + j + "/" + k,
                            ImmutableMap.of("prop1", (Object) "value3")));
                }
            }
        }

        Content content = contentManager.get("/testDeleteContent");
        Assert.assertEquals("/testDeleteContent", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testDeleteContent/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testDeleteContent/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        StorageClientUtils.deleteTree(contentManager, "/testDeleteContent/test/ing");
        content = contentManager.get("/testDeleteContent/test/ing");
        Assert.assertNull(content);
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 5; k++) {
                    Assert.assertNull(contentManager.get("/testDeleteContent/test/ing/" + i + "/" + j + "/" + k));
                }
            }
        }

    }

    @Test
    public void testDeleteContentDeletesPathConsistently() throws StorageClientException, AccessDeniedException
    {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        contentManager.update(new Content("/testDeleteContent", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/testDeleteContent/test", ImmutableMap.of("prop1", (Object) "value2")));

        contentManager.delete("/testDeleteContent/test");

        Assert.assertNull(contentManager.get("/testDeleteContent/test"));

        Content parent = contentManager.get("/testDeleteContent");

        Iterator<Content>
            children = null;
        Iterator<String>
            childPaths = null;

        children = parent.listChildren().iterator();
        childPaths = parent.listChildPaths().iterator();

        Assert.assertFalse(children.hasNext());
        Assert.assertFalse(childPaths.hasNext());

        children = contentManager.listChildren("/testDeleteContent");
        childPaths = contentManager.listChildPaths("/testDeleteContent");

        Assert.assertFalse(children.hasNext());
        Assert.assertFalse(childPaths.hasNext());
    }

    @Test
    public void testUpdateContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        StorageClientUtils.deleteTree(contentManager, "/testUpdateContent");
        contentManager.update(new Content("/testUpdateContent", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/testUpdateContent/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/testUpdateContent/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/testUpdateContent");
        Assert.assertEquals("/testUpdateContent", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testUpdateContent/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testUpdateContent/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        p = content.getProperties();
        Assert.assertNull((String)p.get("prop1update"));

        content.setProperty("prop1update", "value4");
        contentManager.update(content);

        content = contentManager.get(content.getPath());
        p = content.getProperties();
        Assert.assertEquals("value4", (String)p.get("prop1update"));

    }

    @Test
    public void testVersionContent() throws StorageClientException, AccessDeniedException,
            InterruptedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        StorageClientUtils.deleteTree(contentManager, "/testVersionContent");
        contentManager.update(new Content("/testVersionContent", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/testVersionContent/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/testVersionContent/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/testVersionContent");
        Assert.assertEquals("/testVersionContent", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testVersionContent/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testVersionContent/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        p = content.getProperties();
        Assert.assertNull((String)p.get("prop1update"));

        // FIXME: add some version list methods, we have no way of testing if
        // this works.
        String versionName = contentManager.saveVersion("/testVersionContent");

        // must reload after a version save.
        content = contentManager.get("/testVersionContent");

        content.setProperty("prop1update", "value4");
        contentManager.update(content);

        content = contentManager.get("/testVersionContent");
        p = content.getProperties();
        Assert.assertEquals("value4", (String)p.get("prop1update"));

        // just in case the machine is so fast all of that took 1ms
        Thread.sleep(50);

        String versionName2 = contentManager.saveVersion("/testVersionContent");

        Content versionContent = contentManager.getVersion("/testVersionContent", versionName);
        Assert.assertNotNull(versionContent);
        Content versionContent2 = contentManager.getVersion("/testVersionContent", versionName2);
        Assert.assertNotNull(versionContent2);
        List<String> versionList = contentManager.getVersionHistory("/testVersionContent");
        Assert.assertNotNull(versionList);
        Assert.assertArrayEquals("Version List is " + Arrays.toString(versionList.toArray())
                + " expecting " + versionName2 + " then " + versionName, new String[] {
                versionName2, versionName }, versionList.toArray(new String[versionList.size()]));

        Content badVersionContent = contentManager.getVersion("/testVersionContent", "BadVersion");
        Assert.assertNull(badVersionContent);

        // get version with metadata
        Map<String, Object> metadata = ImmutableMap.<String, Object>of("testmd1", "yaymd1", "testmd2", 100);
        String metaVersionName = contentManager.saveVersion("/testVersionContent", metadata);
        Content metaVersion = contentManager.getVersion("/testVersionContent", metaVersionName);
        Assert.assertNotNull(metaVersion);
        Assert.assertEquals("yaymd1", metaVersion.getProperty("metadata:testmd1"));
        Assert.assertEquals(100, metaVersion.getProperty("metadata:testmd2"));
    }

    @Test
    public void testUploadContent() throws StorageClientException, AccessDeniedException {
        AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
        User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

        AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
                currentUser, configuration, sharedCache,  new LoggingStorageListener(), principalValidatorResolver);

        ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
                configuration,  sharedCache, new LoggingStorageListener());
        StorageClientUtils.deleteTree(contentManager, "/testUploadContent");
        contentManager.update(new Content("/testUploadContent", ImmutableMap.of("prop1", (Object) "value1")));
        contentManager.update(new Content("/testUploadContent/test", ImmutableMap.of("prop1", (Object) "value2")));
        contentManager
                .update(new Content("/testUploadContent/test/ing", ImmutableMap.of("prop1", (Object) "value3")));

        Content content = contentManager.get("/testUploadContent");
        Assert.assertEquals("/testUploadContent", content.getPath());
        Map<String, Object> p = content.getProperties();
        Assert.assertEquals("value1", (String)p.get("prop1"));
        Iterator<Content> children = content.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        Content child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testUploadContent/test", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value2", (String)p.get("prop1"));
        children = child.listChildren().iterator();
        Assert.assertTrue(children.hasNext());
        child = children.next();
        Assert.assertFalse(children.hasNext());
        Assert.assertEquals("/testUploadContent/test/ing", child.getPath());
        p = child.getProperties();
        Assert.assertEquals("value3", (String)p.get("prop1"));

        p = content.getProperties();
        Assert.assertNull((String)p.get("prop1update"));

        // FIXME: add some version list methods, we have no way of testing if
        // this works.
        contentManager.saveVersion("/testUploadContent");

        content = contentManager.get("/testUploadContent");

        content.setProperty("prop1update", "value4");
        contentManager.update(content);

        content = contentManager.get(content.getPath());
        p = content.getProperties();
        Assert.assertEquals("value4", (String)p.get("prop1update"));

        final byte[] b = new byte[20 * 1024 * 1024 + 1231];
        Random r = new Random();
        r.nextBytes(b);
        try {
            contentManager.update(new Content("/testUploadContent/test/ing/testfile.txt", ImmutableMap.of(
                    "testproperty", (Object) "testvalue")));
            long su = System.currentTimeMillis();
            ByteArrayInputStream bais = new ByteArrayInputStream(b);
            contentManager.writeBody("/testUploadContent/test/ing/testfile.txt", bais);
            bais.close();
            long eu = System.currentTimeMillis();

            InputStream read = contentManager.getInputStream("/testUploadContent/test/ing/testfile.txt");

            int i = 0;
            byte[] buffer = new byte[8192];
            int j = read.read(buffer);
            Assert.assertNotSame(-1, j);
            while (j != -1) {
                // Assert.assertEquals((int)b[i] & 0xff, j);
                i = i + j;
                j = read.read(buffer);
            }
            read.close();
            Assert.assertEquals(b.length, i);
            long ee = System.currentTimeMillis();
            LOGGER.info("Write rate {} MB/s  Read Rate {} MB/s ",
                    (1000 * (double) b.length / (1024 * 1024 * (double) (eu - su))),
                    (1000 * (double) b.length / (1024 * 1024 * (double) (ee - eu))));

            // Update content and re-read
            r.nextBytes(b);
            bais = new ByteArrayInputStream(b);
            contentManager.writeBody("/testUploadContent/test/ing/testfile.txt", bais);

            read = contentManager.getInputStream("/testUploadContent/test/ing/testfile.txt");

            i = 0;
            j = read.read(buffer);
            Assert.assertNotSame(-1, j);
            while (j != -1) {
                for (int k = 0; k < j; k++) {
                    Assert.assertEquals(b[i], buffer[k]);
                    i++;
                }
                if ((i % 100 == 0) && (i < b.length - 20)) {
                    Assert.assertEquals(10, read.skip(10));
                    i += 10;
                }
                j = read.read(buffer);
            }
            read.close();
            Assert.assertEquals(b.length, i);

        } catch (IOException e) {

            // TODO Auto-generated catch block
            e.printStackTrace();
            Assert.fail();
        }

    }

  @Test
  public void testMoveWithChildren() throws StorageClientException, AccessDeniedException {
    AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
        currentUser, configuration, null, new LoggingStorageListener(), principalValidatorResolver);

    ContentManagerImpl contentManager = new ContentManagerImpl(client,
        accessControlManager, configuration, null, new LoggingStorageListener());
    contentManager.update(new Content("/testMoveWithChildren", ImmutableMap.of("prop1", (Object) "value1")));
    contentManager.update(new Content("/testMoveWithChildren/movewc", ImmutableMap.of("prop1",
        (Object) "value2")));
    contentManager.update(new Content("/testMoveWithChildren/test", ImmutableMap
        .of("prop1", (Object) "value3")));
    contentManager.update(new Content("/testMoveWithChildren/test/ing", ImmutableMap.of("prop1",
        (Object) "value4")));
    StorageClientUtils.deleteTree(contentManager, "/testMoveWithChildren/movewc/test");
    contentManager.move("/testMoveWithChildren/test", "/testMoveWithChildren/movewc/test");

    Content content = contentManager.get("/testMoveWithChildren");
    Assert.assertEquals("/testMoveWithChildren", content.getPath());
    Map<String, Object> p = content.getProperties();
    LOGGER.info("Properties is {}", p);
    Assert.assertEquals("value1", (String) p.get("prop1"));
    Iterator<Content> children = content.listChildren().iterator();
    Assert.assertTrue(children.hasNext());
    Content child = children.next();
    Assert.assertFalse(children.hasNext());
    Assert.assertEquals("/testMoveWithChildren/movewc", child.getPath());
    p = child.getProperties();
    Assert.assertEquals("value2", (String) p.get("prop1"));
    children = child.listChildren().iterator();
    Assert.assertTrue(children.hasNext());
    child = children.next();
    Assert.assertFalse(children.hasNext());
    Assert.assertEquals("/testMoveWithChildren/movewc/test", child.getPath());
    p = child.getProperties();
    Assert.assertEquals("value3", (String) p.get("prop1"));
    children = child.listChildren().iterator();
    Assert.assertTrue(children.hasNext());
    child = children.next();
    Assert.assertFalse(children.hasNext());
    Assert.assertEquals("/testMoveWithChildren/movewc/test/ing", child.getPath());
    p = child.getProperties();
    Assert.assertEquals("value4", (String) p.get("prop1"));

  }

  @Test
  public void testMoveWithForce() throws Exception {
    AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
        currentUser, configuration, null, new LoggingStorageListener(), principalValidatorResolver);

    ContentManagerImpl contentManager = new ContentManagerImpl(client,
        accessControlManager, configuration, null, new LoggingStorageListener());
    contentManager.update(new Content("/testMove", ImmutableMap.<String, Object>of("prop1", "value1")));
    contentManager.update(new Content("/testMoveWithForce", ImmutableMap.<String, Object>of("prop1", "value2")));

    try {
      contentManager.move("/testMove", "/testMoveWithForce", false);
      Assert.fail("Should throw an exception when trying to move without for to an existing path.");
    } catch (StorageClientException e) {
      // expected
    }

    contentManager.move("/testMove", "/testMoveWithForce", true);
  }

  @Test
  public void testMoveWithAcls() throws Exception {
    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    AuthorizableManager adminAuthorizableManager = adminSession.getAuthorizableManager();
    ContentManager adminContentManager = adminSession.getContentManager();
    AccessControlManager adminAccessControlManager = adminSession.getAccessControlManager();

    adminContentManager.update(new Content("/testMove1", ImmutableMap.<String, Object>of("prop1", "value1")));

    // create a test user and some test permissions
    String u1 = "user1-" + System.currentTimeMillis();
    adminAuthorizableManager.createUser(u1, u1, u1, null);
    Authorizable user1 = adminAuthorizableManager.findAuthorizable(u1);

    // setup an acl on the target
    AclModification user1canWrite = new AclModification(AclModification.grantKey(u1),
        Permissions.CAN_WRITE.getPermission(), AclModification.Operation.OP_OR);
    adminAccessControlManager.setAcl(Security.ZONE_CONTENT, "/testMove1", new AclModification[] { user1canWrite });

    // verify we can write to the old location
    Assert.assertTrue(adminAccessControlManager.can(user1, Security.ZONE_CONTENT, "/testMove1", Permissions.CAN_WRITE));

    // move the content
    adminContentManager.move("/testMove1", "/testMove2", false);

    // verify that the ACL moved with the content
    Assert.assertTrue(adminAccessControlManager.can(user1, Security.ZONE_CONTENT, "/testMove2", Permissions.CAN_WRITE));
  }

  @Test
  public void testMoveWithVersions() throws Exception {
    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();

    String from = "testMove1";
    String to = "testMove2";

    // add some initial content
    adminContentManager.update(new Content(from, ImmutableMap.<String, Object>of("prop1", "value1")));

    // save a version of the content and verify the history
    adminContentManager.saveVersion(from);
    List<String> history = adminContentManager.getVersionHistory(from);
    Assert.assertEquals(1, history.size());

    // move the content
    adminContentManager.move(from, to);

    // check the base content is there
    Assert.assertTrue(adminContentManager.exists(to));

    // check the history
    history = adminContentManager.getVersionHistory(to);
    Assert.assertEquals(1, history.size());
  }

  @Test
  public void testMoveWithDestinationHistory() throws Exception {
    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();

    String from = "testMove1";
    String to = "testMove2";

    // add some initial content
    adminContentManager.update(new Content(from, ImmutableMap.<String, Object>of("prop1", "value1")));
    adminContentManager.update(new Content(to, ImmutableMap.<String, Object>of("prop2", "value2")));

    // save a version of the content and verify the history
    adminContentManager.saveVersion(from);
    List<String> history = adminContentManager.getVersionHistory(from);
    Assert.assertEquals(1, history.size());

    adminContentManager.saveVersion(to);
    adminContentManager.saveVersion(to);
    adminContentManager.saveVersion(to);
    history = adminContentManager.getVersionHistory(to);
    Assert.assertEquals(3, history.size());

    // move the content
    adminContentManager.move(from, to, true);

    // check the base content is there
    Assert.assertFalse(adminContentManager.exists(from));
    Assert.assertTrue(adminContentManager.exists(to));

    // ensure we don't have properties from the previous content version
    Content movedTo = adminContentManager.get(to);
    Assert.assertNull(movedTo.getProperty("prop2"));
    Assert.assertEquals("value1", movedTo.getProperty("prop1"));

    // check the history
    history = adminContentManager.getVersionHistory(to);
    Assert.assertEquals(3, history.size());
  }

  @Test
  public void testMoveWithoutDestinationHistory() throws Exception {
    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();

    String from = "testMove1";
    String to = "testMove2";

    // add some initial content
    adminContentManager.update(new Content(from, ImmutableMap.<String, Object>of("prop1", "value1")));
    adminContentManager.update(new Content(to, ImmutableMap.<String, Object>of("prop2", "value2")));

    // save a version of the content and verify the history
    adminContentManager.saveVersion(from);
    List<String> history = adminContentManager.getVersionHistory(from);
    Assert.assertEquals(1, history.size());

    adminContentManager.saveVersion(to);
    adminContentManager.saveVersion(to);
    adminContentManager.saveVersion(to);
    history = adminContentManager.getVersionHistory(to);
    Assert.assertEquals(3, history.size());

    // move the content
    adminContentManager.move(from, to, true, false);

    // check the base content is there
    Assert.assertTrue(adminContentManager.exists(to));

    // ensure we don't have properties from the previous content version
    Content movedTo = adminContentManager.get(to);
    Assert.assertNull(movedTo.getProperty("prop2"));
    Assert.assertEquals("value1", movedTo.getProperty("prop1"));

    // check the history
    history = adminContentManager.getVersionHistory(to);
    Assert.assertEquals(1, history.size());
  }

  @Test
  public void testMoveThenVersionAtDepth() throws Exception {
    Repository repository = (Repository) new BaseMemoryRepository().getRepository();
    Session adminSession = repository.loginAdministrative();
    ContentManager adminContentManager = adminSession.getContentManager();

    String from = "testMove1";
    String tmp = "tmp";
    String to = from + "/" + tmp;

    // add some initial content
    adminContentManager.update(new Content(from, ImmutableMap.<String, Object>of("prop1", "value1")));
    adminContentManager.update(new Content(tmp, ImmutableMap.<String, Object>of("prop1", "value1")));

    // save a version of the content and verify the history
    adminContentManager.saveVersion(from);
    List<String> history = adminContentManager.getVersionHistory(from);
    Assert.assertEquals(1, history.size());
    
    adminContentManager.saveVersion(tmp);
    history = adminContentManager.getVersionHistory(tmp);
    Assert.assertEquals(1, history.size());

    // move the content
    adminContentManager.move(tmp, to);

    // check the base content is there
    Assert.assertTrue(adminContentManager.exists(to));

    // check that all history is still there
    history = adminContentManager.getVersionHistory(from);
    Assert.assertEquals(1, history.size());
    history = adminContentManager.getVersionHistory(to);
    Assert.assertEquals(1, history.size());

    // verify that we can add more history at each node
    adminContentManager.saveVersion(from);
    history = adminContentManager.getVersionHistory(from);
    Assert.assertEquals(2, history.size());

    adminContentManager.saveVersion(to);
    history = adminContentManager.getVersionHistory(to);
    Assert.assertEquals(2, history.size());
  }

  @Test
  public void testCanReuseAContentPath() throws Exception {
      String path = "/pathToReuse" + System.currentTimeMillis();
      AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
      User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

      AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
          currentUser, configuration, sharedCache, new LoggingStorageListener(), principalValidatorResolver);

      ContentManagerImpl contentManager = new ContentManagerImpl(client,
          accessControlManager, configuration, sharedCache, new LoggingStorageListener());
      contentManager.update(new Content(path, ImmutableMap.of("prop1", (Object) "value1", "prop2", "valueProp2")));
      Content content = contentManager.get(path);
      Assert.assertEquals("This property should have been updated.", content.getProperty("prop1"), "value1");
      Assert.assertEquals("This property should have been updated.", content.getProperty("prop2"), "valueProp2");
      contentManager.delete(path);
      content = contentManager.get(path);
      Assert.assertNull(content);
      contentManager.update(new Content(path, ImmutableMap.of("prop1", (Object) "value2")));
      content = contentManager.get(path);
      Assert.assertNotNull(content);
      Assert.assertEquals("This property should have been updated.", content.getProperty("prop1"), "value2");
      Assert.assertFalse("This property should have been updated.", content.hasProperty("prop2"));
  }

  @Test
  public void testListChildren() throws StorageClientException, AccessDeniedException {
	  AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
	    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

	    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
	        currentUser, configuration, null, new LoggingStorageListener(), principalValidatorResolver);

	    ContentManagerImpl contentManager = new ContentManagerImpl(client,
	        accessControlManager, configuration, null, new LoggingStorageListener());

	    Iterator<Content> children = contentManager.listChildren("/testListChildrenDoesNotExist");
	    Assert.assertEquals(0, Iterators.size(children));

	    StorageClientUtils.deleteTree(contentManager, "/testListChildren");
	    contentManager.update(new Content("/testListChildren", ImmutableMap.of("prop1", (Object) "parent")));
	    children = contentManager.listChildren("/testListChildren");
	    Assert.assertEquals(0, Iterators.size(children));

	    contentManager.update(new Content("/testListChildren/child1", ImmutableMap.of("someprop1", (Object) "value1")));
	    contentManager.update(new Content("/testListChildren/child2", ImmutableMap.of("someprop1", (Object) "value2")));
	    contentManager.update(new Content("/testListChildren/child3", ImmutableMap.of("someprop1",(Object) "value3")));
	    contentManager.update(new Content("/youreNotMyDad/child4", ImmutableMap.of("someprop1",(Object) "value4")));

	    children = contentManager.listChildren("/testListChildren");
	    int childCount = 0;
	    while (children.hasNext()){
            // Make sure we're getting back the children we saved
            Assert.assertNotNull(children.next().getProperty("someprop1"));
            childCount++;
	    }
	    Assert.assertEquals(3, childCount);
  }

  // @Test This Test runs forever and tests for OOM on disposables.
  public void testOOM() throws StorageClientException, AccessDeniedException {
      AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
      User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

      AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
              currentUser, configuration, null,  new LoggingStorageListener(), principalValidatorResolver);

      ContentManagerImpl contentManager = new ContentManagerImpl(client, accessControlManager,
              configuration, null,  new LoggingStorageListener());
      contentManager.update(new Content("/testCreateContent", ImmutableMap.of("prop1", (Object) "value1")));
      contentManager.update(new Content("/testCreateContent/test", ImmutableMap.of("prop1", (Object) "value2")));
      contentManager
              .update(new Content("/testCreateContent/test/ing", ImmutableMap.of("prop1", (Object) "value3")));


      Content obj = contentManager.get("/testCreateContent");
      Assert.assertNotNull(obj);
      while (true) {
        for (@SuppressWarnings("unused") Content child : obj.listChildren()) {
        }
      }
  }


  @Test
  public void testTrigger() throws StorageClientException, AccessDeniedException {
    AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
        currentUser, configuration, null, new LoggingStorageListener(), principalValidatorResolver);

    ContentManagerImpl contentManager = new ContentManagerImpl(client,
        accessControlManager, configuration, null, new LoggingStorageListener());
    contentManager.update(new Content("/testMoveWithChildren", ImmutableMap.of("prop1", (Object) "value1")));
    contentManager.update(new Content("/testMoveWithChildren/movewc", ImmutableMap.of("prop1",
        (Object) "value2")));
    contentManager.update(new Content("/testMoveWithChildren/test", ImmutableMap
        .of("prop1", (Object) "value3")));
    contentManager.update(new Content("/testMoveWithChildren/test/ing", ImmutableMap.of("prop1",
        (Object) "value4")));
    contentManager.triggerRefresh("/testMoveWithChildren/test/ing");
  }

  @Test
  public void testTriggerAll() throws StorageClientException, AccessDeniedException {
    AuthenticatorImpl AuthenticatorImpl = new AuthenticatorImpl(client, configuration, null);
    User currentUser = AuthenticatorImpl.authenticate("admin", "admin");

    AccessControlManagerImpl accessControlManager = new AccessControlManagerImpl(client,
        currentUser, configuration, null, new LoggingStorageListener(), principalValidatorResolver);

    ContentManagerImpl contentManager = new ContentManagerImpl(client,
        accessControlManager, configuration, null, new LoggingStorageListener());
    contentManager.update(new Content("/testMoveWithChildren", ImmutableMap.of("prop1", (Object) "value1")));
    contentManager.update(new Content("/testMoveWithChildren/movewc", ImmutableMap.of("prop1",
        (Object) "value2")));
    contentManager.update(new Content("/testMoveWithChildren/test", ImmutableMap
        .of("prop1", (Object) "value3")));
    contentManager.update(new Content("/testMoveWithChildren/test/ing", ImmutableMap.of("prop1",
        (Object) "value4")));
    contentManager.triggerRefreshAll();
  }
  
  /**
   * Verify that a user can successfully delete a content node by virtue of having write on its
   * parent, while not having delete permission on the node itself. This is considered a valid
   * scenario.
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testCanDeleteWithParentWrite() throws StorageClientException, AccessDeniedException {
    String parentPath = "/ContentManagerTest/testCanDeleteWithParentWrite/parent";
    String childPath1 = StorageClientUtils.newPath(parentPath, "child1");
    String childPath2 = StorageClientUtils.newPath(childPath1, "child2");
    
    Repository repository = new RepositoryImpl(configuration, clientPool, new LoggingStorageListener());
    
    Session adminSession = repository.loginAdministrative();
    adminSession.getAuthorizableManager().createUser("test", "test", "test", new HashMap<String, Object>());
    
    AccessControlManager accessControlManager = adminSession.getAccessControlManager();
    ContentManager contentManager = adminSession.getContentManager();
    contentManager.update(new Content(parentPath, ImmutableMap.<String, Object>of("test", "value")));
    contentManager.update(new Content(childPath2, ImmutableMap.<String, Object>of("test", "value")));
    
    boolean couldNotDelete = false;
    
    // sanity check the permission setup
    Session userSession = null;
    try {
      userSession = repository.login("test", "test");
      userSession.getContentManager().delete(childPath2);
      userSession.logout();
    } catch (AccessDeniedException e) {
      couldNotDelete = true;
    }
    Assert.assertTrue(couldNotDelete);
    
    // give access by virtue of giving CAN_WRITE to an ancestor
    accessControlManager.setAcl(Security.ZONE_CONTENT, parentPath, new AclModification[] {
       new AclModification(AclModification.grantKey("test"), Permissions.CAN_WRITE.getPermission(),
           AclModification.Operation.OP_REPLACE)
    });
    
    // try again, it should succeed
    userSession = repository.login("test", "test");
    userSession.getContentManager().delete(childPath2);
    userSession.logout();
    
    Assert.assertTrue(contentManager.exists(parentPath));
    Assert.assertTrue(contentManager.exists(childPath1));
    Assert.assertFalse(contentManager.exists(childPath2));
    
  }


  /**
   * Verify that a user has access to move content from the source to the destination when they
   * only have write as the root permission. The key functional scenario this ensures is that a
   * user who has access to edit pooled content will have sufficient access to "publish" their
   * changes to the live page.
   * 
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  @Test
  public void testCanMoveWithParentWrite() throws StorageClientException, AccessDeniedException {
    String parentSourcePath = "/ContentManagerTest/testCanDeleteWithParentWrite/source/parent";
    String parentDestPath = "/ContentManagerTest/testCanDeleteWithParentWrite/dest/parent";
    String childSourcePath = StorageClientUtils.newPath(parentSourcePath, "child");
    String childDestPath = StorageClientUtils.newPath(parentDestPath, "child");
    
    // create a non-admin user "test"
    Repository repository = new RepositoryImpl(configuration, clientPool, new LoggingStorageListener());
    Session adminSession = repository.loginAdministrative();
    adminSession.getAuthorizableManager().createUser("test", "test", "test", new HashMap<String, Object>());
    
    // seed the test content
    AccessControlManager accessControlManager = adminSession.getAccessControlManager();
    ContentManager contentManager = adminSession.getContentManager();
    contentManager.update(new Content(childSourcePath, ImmutableMap.<String, Object>of("test", "value-source")));
    contentManager.update(new Content(childDestPath, ImmutableMap.<String, Object>of("test", "value-dest")));
    
    Session userSession = null;

    // give access by virtue of giving CAN_WRITE to parent
    accessControlManager.setAcl(Security.ZONE_CONTENT, parentSourcePath, new AclModification[] {
       new AclModification(AclModification.grantKey("test"), Permissions.CAN_WRITE.combine(
           Permissions.CAN_READ).getPermission(), AclModification.Operation.OP_REPLACE)
    });
    accessControlManager.setAcl(Security.ZONE_CONTENT, parentDestPath, new AclModification[] {
        new AclModification(AclModification.grantKey("test"), Permissions.CAN_WRITE.combine(
            Permissions.CAN_READ).getPermission(), AclModification.Operation.OP_REPLACE)
     });
    
    // move should be successful
    userSession = repository.login("test", "test");
    userSession.getContentManager().move(childSourcePath, childDestPath, true);
    userSession.logout();
    
    // verify the remaining content
    Assert.assertTrue(contentManager.exists(childDestPath));
    Assert.assertEquals("value-source", contentManager.get(childDestPath).getProperty("test"));
    Assert.assertFalse(contentManager.exists(childSourcePath));
    
  }

}
