package org.ergoplatform.dex.executor.orders

import cats.effect.{Blocker, ExitCode, Resource}
import fs2.Chunk
import monix.eval.Task
import org.ergoplatform.dex.EnvApp
import org.ergoplatform.dex.clients.{ErgoNetwork, StreamingErgoNetworkClient}
import org.ergoplatform.dex.domain.orderbook.Order.AnyOrder
import org.ergoplatform.dex.domain.orderbook.Trade.AnyTrade
import org.ergoplatform.dex.domain.orderbook.{OrderId, TradeId}
import org.ergoplatform.dex.executor.orders.config.ConfigBundle
import org.ergoplatform.dex.executor.orders.context.AppContext
import org.ergoplatform.dex.executor.orders.processes.OrdersExecutor
import org.ergoplatform.dex.executor.orders.services.ExecutionService
import org.ergoplatform.dex.executor.orders.streaming.StreamingBundle
import org.ergoplatform.dex.streaming.{Consumer, MakeKafkaConsumer, Producer}
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import tofu.WithRun
import tofu.fs2Instances._
import tofu.lift.IsoK
import tofu.syntax.unlift._

object App extends EnvApp[AppContext] {

  def run(args: List[String]): Task[ExitCode] =
    init(args.headOption).use { case (executor, ctx) =>
      val appF = executor.run.compile.drain
      appF.run(ctx) as ExitCode.Success
    }

  private def init(configPathOpt: Option[String]): Resource[InitF, (OrdersExecutor[StreamF], AppContext)] =
    for {
      blocker <- Blocker[InitF]
      configs <- Resource.eval(ConfigBundle.load(configPathOpt))
      ctx                                                       = AppContext.init(configs)
      implicit0(mc: MakeKafkaConsumer[RunF, TradeId, AnyTrade]) = MakeKafkaConsumer.make[InitF, RunF, TradeId, AnyTrade]
      implicit0(isoKRun: IsoK[RunF, InitF])                     = IsoK.byFunK(wr.runContextK(ctx))(wr.liftF)
      consumer                                                  = Consumer.make[StreamF, RunF, TradeId, AnyTrade]
      producer <- Producer.make[InitF, StreamF, RunF, OrderId, AnyOrder](???, configs.producer)
      implicit0(streaming: StreamingBundle[StreamF, RunF]) = StreamingBundle(consumer, producer)
      implicit0(backend: SttpBackend[RunF, Fs2Streams[RunF]]) <- makeBackend(ctx, blocker)
      implicit0(client: ErgoNetwork[RunF]) = StreamingErgoNetworkClient.make[StreamF, RunF]
      implicit0(service: ExecutionService[RunF]) <- Resource.eval(ExecutionService.make[InitF, RunF])
      executor                                   <- Resource.eval(OrdersExecutor.make[InitF, StreamF, RunF, Chunk])
    } yield executor -> ctx

  private def makeBackend(
    ctx: AppContext,
    blocker: Blocker
  )(implicit wr: WithRun[RunF, InitF, AppContext]): Resource[InitF, SttpBackend[RunF, Fs2Streams[RunF]]] =
    Resource
      .eval(wr.concurrentEffect)
      .flatMap(implicit ce => AsyncHttpClientFs2Backend.resource[RunF](blocker))
      .mapK(wr.runContextK(ctx))
}