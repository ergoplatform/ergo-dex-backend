package org.ergoplatform.dex.domain.amm

import derevo.circe.{decoder, encoder}
import derevo.derive
import org.ergoplatform.ergo.Address
import org.ergoplatform.dex.domain.AssetAmount
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class RedeemParams(lp: AssetAmount, dexFee: Long, p2pk: Address)
