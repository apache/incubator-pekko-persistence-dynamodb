/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.persistence.dynamodb.query

import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.persistence.dynamodb.query.javadsl.{ DynamoDBReadJournal => JavaDynamoDBReadJournal }
import org.apache.pekko.persistence.dynamodb.query.scaladsl.internal.{ DynamoDBReadJournal => ScalaDynamoDBReadJournal }
import org.apache.pekko.persistence.query.ReadJournalProvider
import org.apache.pekko.persistence.query.javadsl.{ ReadJournal => JavaReadJournal }
import org.apache.pekko.persistence.query.scaladsl.{ ReadJournal => ScalaReadJournal }
import com.typesafe.config.Config

class DynamoDBReadJournalProvider(system: ExtendedActorSystem, config: Config, configPath: String)
    extends ReadJournalProvider {
  private lazy val _scalaReadJournal = new ScalaDynamoDBReadJournal(config, configPath)(system)
  override def scaladslReadJournal(): ScalaReadJournal = _scalaReadJournal

  private lazy val _javadslReadJournal = new JavaDynamoDBReadJournal(_scalaReadJournal)
  override def javadslReadJournal(): JavaReadJournal = _javadslReadJournal
}

trait ReadJournalSettingsProvider {
  protected def readJournalSettings: DynamoDBReadJournalConfig
}
