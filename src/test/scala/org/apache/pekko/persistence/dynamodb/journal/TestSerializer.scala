/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.dynamodb.journal

import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.serialization.{ AsyncSerializerWithStringManifest, JavaSerializer }

import scala.concurrent.{ ExecutionContext, Future }

class TestSerializer(system: ExtendedActorSystem) extends AsyncSerializerWithStringManifest(system) {

  override def identifier: Int = 2255

  override def manifest(o: AnyRef): String = o.getClass.getName

  implicit val ec: ExecutionContext = system.dispatcher

  val javaSerializer: JavaSerializer = new JavaSerializer(system)

  override def fromBinaryAsync(bytes: Array[Byte], manifest: String): Future[AnyRef] =
    Future.successful(javaSerializer.fromBinary(bytes, Class.forName(manifest)))

  override def toBinaryAsync(obj: AnyRef): Future[Array[Byte]] =
    Future.successful(javaSerializer.toBinary(obj))

}
