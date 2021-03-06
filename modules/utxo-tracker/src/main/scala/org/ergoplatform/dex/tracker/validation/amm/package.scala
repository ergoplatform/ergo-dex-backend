package org.ergoplatform.dex.tracker.validation

import cats.{FlatMap, Monad}
import org.ergoplatform.dex.configs.ExecutionConfig
import org.ergoplatform.dex.domain.NetworkContext
import org.ergoplatform.dex.domain.amm.CFMMOperationRequest
import org.ergoplatform.ergo.ErgoNetwork
import tofu.higherKind.Embed
import tofu.syntax.context._
import tofu.syntax.embed._
import tofu.syntax.monadic._

package object amm {

  type CFMMRules[F[_]] = CFMMOperationRequest => F[Boolean]

  implicit def embed: Embed[CFMMRules] =
    new Embed[CFMMRules] {

      def embed[F[_]: FlatMap](ft: F[CFMMRules[F]]): CFMMRules[F] =
        op => ft >>= (rules => rules(op))
    }

  object CFMMRules {

    def make[F[_]: Monad: ExecutionConfig.Has](implicit network: ErgoNetwork[F]): CFMMRules[F] =
      (
        for {
          conf       <- context
          networkCtx <- NetworkContext.make
        } yield new CfmmRuleDefs[F](conf, networkCtx).rules
      ).embed
  }
}
