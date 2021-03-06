package org.ergoplatform.dex.domain.syntax

import cats.syntax.either._
import org.ergoplatform.dex.domain.orderbook.OrderType.{Ask, Bid}
import org.ergoplatform.dex.domain.orderbook.Trade
import org.ergoplatform.dex.domain.orderbook.Trade.AnyTrade

object trade {

  implicit final class AnyMatchOps(private val trade: AnyTrade) extends AnyVal {

    def refine: Either[Trade[Bid, Ask], Trade[Ask, Bid]] =
      trade match {
        case t: Trade[Ask, Bid] @unchecked if trade.order.base.`type`.isAsk => t.asRight
        case t: Trade[Bid, Ask] @unchecked                                  => t.asLeft
      }
  }
}
