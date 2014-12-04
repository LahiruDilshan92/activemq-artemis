/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.core.server.cluster.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.api.core.ActiveMQBuffer;
import org.apache.activemq.api.core.ActiveMQBuffers;
import org.apache.activemq.api.core.BroadcastEndpoint;
import org.apache.activemq.api.core.BroadcastEndpointFactory;
import org.apache.activemq.api.core.SimpleString;
import org.apache.activemq.api.core.TransportConfiguration;
import org.apache.activemq.api.core.management.CoreNotificationType;
import org.apache.activemq.core.server.ActiveMQServerLogger;
import org.apache.activemq.core.server.NodeManager;
import org.apache.activemq.core.server.cluster.BroadcastGroup;
import org.apache.activemq.core.server.management.Notification;
import org.apache.activemq.core.server.management.NotificationService;
import org.apache.activemq.utils.TypedProperties;
import org.apache.activemq.utils.UUIDGenerator;

/**
 * <p>This class will use the {@link BroadcastEndpoint} to send periodical updates on the list for connections
 *    used by this server. </p>
 *
 * <p>This is totally generic to the mechanism used on the transmission. It originally only had UDP but this got refactored
 * into sub classes of {@link BroadcastEndpoint}</p>
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author Clebert Suconic
 *
 * Created 15 Nov 2008 09:45:32
 *
 */
public class BroadcastGroupImpl implements BroadcastGroup, Runnable
{
   private final NodeManager nodeManager;

   private final String name;

   private final List<TransportConfiguration> connectors = new ArrayList<TransportConfiguration>();

   private boolean started;

   private final long broadCastPeriod;

   private final ScheduledExecutorService scheduledExecutor;

   private ScheduledFuture<?> future;

   private boolean loggedBroadcastException = false;

   // Each broadcast group has a unique id - we use this to detect when more than one group broadcasts the same node id
   // on the network which would be an error
   private final String uniqueID;

   private NotificationService notificationService;

   private BroadcastEndpoint endpoint;


   public BroadcastGroupImpl(final NodeManager nodeManager,
                                  final String name,
                                  final long broadCastPeriod,
                                  final ScheduledExecutorService scheduledExecutor,
                                  final BroadcastEndpointFactory endpointFactory) throws Exception
   {
      this.nodeManager = nodeManager;

      this.name = name;

      this.scheduledExecutor = scheduledExecutor;

      this.broadCastPeriod = broadCastPeriod;

      this.endpoint = endpointFactory.createBroadcastEndpoint();

      uniqueID = UUIDGenerator.getInstance().generateStringUUID();
   }

   public void setNotificationService(final NotificationService notificationService)
   {
      this.notificationService = notificationService;
   }

   public synchronized void start() throws Exception
   {
      if (started)
      {
         return;
      }

      endpoint.openBroadcaster();

      started = true;

      if (notificationService != null)
      {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), new SimpleString(name));
         Notification notification = new Notification(nodeManager.getNodeId().toString(), CoreNotificationType.BROADCAST_GROUP_STARTED, props);
         notificationService.sendNotification(notification);
      }

      activate();
   }

   public synchronized void stop()
   {
      if (!started)
      {
         return;
      }

      if (future != null)
      {
         future.cancel(false);
      }

      try
      {
         endpoint.close(true);
      }
      catch (Exception e1)
      {
         ActiveMQServerLogger.LOGGER.broadcastGroupClosed(e1);
      }

      started = false;

      if (notificationService != null)
      {
         TypedProperties props = new TypedProperties();
         props.putSimpleStringProperty(new SimpleString("name"), new SimpleString(name));
         Notification notification = new Notification(nodeManager.getNodeId().toString(), CoreNotificationType.BROADCAST_GROUP_STOPPED, props);
         try
         {
            notificationService.sendNotification(notification);
         }
         catch (Exception e)
         {
            ActiveMQServerLogger.LOGGER.broadcastGroupClosed(e);
         }
      }

   }

   public synchronized boolean isStarted()
   {
      return started;
   }

   public String getName()
   {
      return name;
   }

   public synchronized void addConnector(final TransportConfiguration tcConfig)
   {
      connectors.add(tcConfig);
   }

   public synchronized void removeConnector(final TransportConfiguration tcConfig)
   {
      connectors.remove(tcConfig);
   }

   public synchronized int size()
   {
      return connectors.size();
   }

   private synchronized void activate()
   {
      if (scheduledExecutor != null)
      {
         future = scheduledExecutor.scheduleWithFixedDelay(this,
            0L,
            broadCastPeriod,
            TimeUnit.MILLISECONDS);
      }
   }

   public synchronized void broadcastConnectors() throws Exception
   {
      ActiveMQBuffer buff = ActiveMQBuffers.dynamicBuffer(4096);

      buff.writeString(nodeManager.getNodeId().toString());

      buff.writeString(uniqueID);

      buff.writeInt(connectors.size());

      for (TransportConfiguration tcConfig : connectors)
      {
         tcConfig.encode(buff);
      }

      byte[] data = buff.toByteBuffer().array();

      endpoint.broadcast(data);
   }

   public void run()
   {
      if (!started)
      {
         return;
      }

      try
      {
         broadcastConnectors();
         loggedBroadcastException = false;
      }
      catch (Exception e)
      {
         // only log the exception at ERROR level once, even if it fails multiple times in a row - HORNETQ-919
         if (!loggedBroadcastException)
         {
            ActiveMQServerLogger.LOGGER.errorBroadcastingConnectorConfigs(e);
            loggedBroadcastException = true;
         }
         else
         {
            ActiveMQServerLogger.LOGGER.debug("Failed to broadcast connector configs...again", e);
         }
      }
   }

}