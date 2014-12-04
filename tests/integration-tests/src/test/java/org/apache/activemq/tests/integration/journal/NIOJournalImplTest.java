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
package org.apache.activemq.tests.integration.journal;

import java.io.File;

import org.apache.activemq.core.journal.SequentialFileFactory;
import org.apache.activemq.core.journal.impl.NIOSequentialFileFactory;
import org.apache.activemq.tests.integration.IntegrationTestLogger;
import org.apache.activemq.tests.unit.core.journal.impl.JournalImplTestUnit;

/**
 *
 * A RealJournalImplTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 */
public class NIOJournalImplTest extends JournalImplTestUnit
{
   private static final IntegrationTestLogger log = IntegrationTestLogger.LOGGER;

   @Override
   protected SequentialFileFactory getFileFactory() throws Exception
   {
      File file = new File(getTestDir());

      NIOJournalImplTest.log.debug("deleting directory " + getTestDir());

      deleteDirectory(file);

      file.mkdir();

      return new NIOSequentialFileFactory(getTestDir(), true);
   }

   @Override
   protected int getAlignment()
   {
      return 1;
   }

}