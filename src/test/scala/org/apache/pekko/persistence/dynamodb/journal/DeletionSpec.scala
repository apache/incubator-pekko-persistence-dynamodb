/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/**
 * Copyright (C) 2016 Typesafe Inc. <http://www.typesafe.com>
 */

package org.apache.pekko.persistence.dynamodb.journal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.persistence.JournalProtocol._
import org.apache.pekko.persistence._
import org.apache.pekko.persistence.dynamodb.IntegSpec
import org.apache.pekko.persistence.dynamodb.query.scaladsl.DynamoDBReadJournal
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.{ Materializer, SystemMaterializer }
import org.apache.pekko.testkit._
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DeletionSpec
    extends TestKit(ActorSystem("DeletionSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures
    with TypeCheckedTripleEquals
    with DynamoDBUtils
    with IntegSpec {

  override def beforeAll(): Unit = {
    super.beforeAll()
    ensureJournalTableExists()
  }

  override def afterAll(): Unit = {
    /*
     * The last operation is a ListAll which may spawn requests that linger
     * past the end of the test case; nothing bad would happen apart from some
     * noisy logging, and I like my build output clean and green.
     */
    Thread.sleep(500)
    dynamo.shutdown()
    system.terminate().futureValue
    queries.close()
    super.afterAll()
  }

  override val persistenceId = "DeletionSpec"
  implicit val materializer: Materializer = SystemMaterializer(system).materializer
  lazy val journal = Persistence(system).journalFor("")
  lazy val queries = PersistenceQuery(system).readJournalFor[DynamoDBReadJournal](DynamoDBReadJournal.Identifier)
  val msgs = (1 to 149).map(i => s"a-$i")
  val more = (150 to 200).map(i => s"b-$i")

  "DynamoDB Journal (Deletion)" must {

    "1 purge events" in {
      journal ! Purge(persistenceId, testActor)
      expectMsg(Purged(persistenceId))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set.empty, Nil))
    }

    "2 store events" in {
      journal ! WriteMessages(msgs.map(m => AtomicWrite(persistentRepr(m))), testActor, 1)
      expectMsg(WriteMessagesSuccessful)
      (1 to 149).foreach(i => expectMsgType[WriteMessageSuccess].persistent.sequenceNr.toInt should ===(i))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set(100L), 1L to 149))

      journal ! WriteMessages(AtomicWrite(more.map(m => persistentRepr(m))) :: Nil, testActor, 1)
      expectMsg(WriteMessagesSuccessful)
      (150 to 200).foreach(i => expectMsgType[WriteMessageSuccess].persistent.sequenceNr.toInt should ===(i))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set(100L, 200L), 1L to 200))
    }

    "3 delete some events" in {
      journal ! DeleteMessagesTo(persistenceId, 5L, testActor)
      expectMsg(DeleteMessagesSuccess(5L))
      journal ! ListAll(persistenceId, testActor)
      val expectedSeqNr = 6L to 200
      expectMsg(ListAllResult(persistenceId, Set(6L), Set(100L, 200L), expectedSeqNr))

      val currentEvents =
        queries.currentEventsByPersistenceId(persistenceId).runWith(Sink.collection).futureValue.toSeq

      currentEvents.map(_.sequenceNr) shouldBe expectedSeqNr
      currentEvents.map(_.event) shouldBe (msgs ++ more).drop(5)
    }

    "4 delete no events" in {
      journal ! DeleteMessagesTo(persistenceId, 3L, testActor)
      expectMsg(DeleteMessagesSuccess(3L))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set(6L), Set(100L, 200L), 6L to 200))
    }

    "5 delete all events" in {
      journal ! DeleteMessagesTo(persistenceId, 210L, testActor)
      expectMsg(DeleteMessagesSuccess(210L))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set(6L, 201L), Set(100L, 200L), Nil))
    }

    "6 purge events" in {
      journal ! Purge(persistenceId, testActor)
      expectMsg(Purged(persistenceId))
      journal ! ListAll(persistenceId, testActor)
      expectMsg(ListAllResult(persistenceId, Set.empty, Set.empty, Nil))
    }

  }

}
