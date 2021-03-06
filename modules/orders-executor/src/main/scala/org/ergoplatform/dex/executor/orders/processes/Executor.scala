package org.ergoplatform.dex.executor.orders.processes

import cats.{Foldable, Functor, Monad}
import derevo.derive
import org.ergoplatform.dex.executor.orders.domain.errors.ExecutionFailure
import org.ergoplatform.dex.executor.orders.services.Execution
import org.ergoplatform.dex.executor.orders.streaming.StreamingBundle
import org.ergoplatform.common.streaming.syntax._
import org.ergoplatform.common.streaming.{CommitPolicy, Record}
import mouse.any._
import org.ergoplatform.dex.domain.orderbook.Order.AnyOrder
import org.ergoplatform.dex.domain.orderbook.OrderId
import tofu.Handle
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.streams.{Evals, Temporal}
import tofu.syntax.context._
import tofu.syntax.embed._
import tofu.syntax.handle._
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.syntax.streams.all._

@derive(representableK)
trait Executor[F[_]] {

  def run: F[Unit]
}

object Executor {

  def make[
    I[_]: Functor,
    F[_]: Monad: Evals[*[_], G]: Temporal[*[_], C]: CommitPolicy.Has: Handle[*[_], ExecutionFailure],
    G[_]: Monad,
    C[_]: Foldable
  ](implicit streaming: StreamingBundle[F, G], executor: Execution[G], logs: Logs[I, G]): I[Executor[F]] =
    logs.forService[Executor[F]] map { implicit l =>
      (context[F] map (policy => new Live[F, G, C](policy): Executor[F])).embed
    }

  final private class Live[
    F[_]: Monad: Evals[*[_], G]: Temporal[*[_], C]: Handle[*[_], ExecutionFailure],
    G[_]: Monad: Logging,
    C[_]: Foldable
  ](commitPolicy: CommitPolicy)(implicit
    streaming: StreamingBundle[F, G],
    executor: Execution[G]
  ) extends Executor[F] {

    def run: F[Unit] =
      streaming.consumer.stream
        .flatTap { rec =>
          val trade = rec.message
          eval(executor.execute(trade))
            .handleWith[ExecutionFailure] { e =>
              val rotateOrders = trade.orders.map(o => Record[OrderId, AnyOrder](o.base.id, o.base))
              eval(warnCause"Trade [${trade.id}] execution failed. $trade" (e)) >>
              emits(rotateOrders).thrush(streaming.producer.produce)
            }
        }
        .commitBatchWithin[C](commitPolicy.maxBatchSize, commitPolicy.commitTimeout)
  }
}
