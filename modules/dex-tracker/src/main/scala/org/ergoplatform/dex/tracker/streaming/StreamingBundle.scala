package org.ergoplatform.dex.tracker.streaming

import org.ergoplatform.dex.domain.models.Order.AnyOrder
import org.ergoplatform.dex.protocol.models.Transaction
import org.ergoplatform.dex.streaming.{Consumer, Producer}

final case class StreamingBundle[F[_], G[_]](
  producer: Producer[AnyOrder, F],
  consumer: Consumer[String, Transaction, F, G]
)