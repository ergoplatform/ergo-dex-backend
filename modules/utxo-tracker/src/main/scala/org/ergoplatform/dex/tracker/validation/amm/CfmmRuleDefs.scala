package org.ergoplatform.dex.tracker.validation.amm

import cats.Applicative
import org.ergoplatform.dex.configs.ExecutionConfig
import org.ergoplatform.dex.domain.NetworkContext
import org.ergoplatform.dex.domain.amm.{CFMMOperationRequest, Deposit, Redeem, Swap}
import tofu.syntax.embed._
import tofu.syntax.monadic._

import scala.{PartialFunction => ?=>}

final class CfmmRuleDefs[F[_]: Applicative](constraints: ExecutionConfig, network: NetworkContext) {

  type CFMMRule = CFMMOperationRequest ?=> Boolean

  def rules: CFMMRules[F] = op => allRules.lift(op).getOrElse(true).pure

  private val allRules = sufficientValueDepositRedeem orElse sufficientValueSwap

  private val safeMinValue = network.params.safeMinValue

  private def sufficientValueDepositRedeem: CFMMRule = {
    case Deposit(_, params, _) => params.dexFee - constraints.minerFee >= safeMinValue
    case Redeem(_, params, _)  => params.dexFee - constraints.minerFee >= safeMinValue
  }

  private def sufficientValueSwap: CFMMRule = { case Swap(_, params, _) =>
    params.dexFeePerToken * params.minOutput.value - constraints.minerFee >= safeMinValue
  }
}
