package org.ergoplatform.dex.domain.amm

import org.ergoplatform.dex.domain.{AssetAmount, BoxInfo}
import org.ergoplatform.dex.protocol.amm.constants

final case class CfmmPool(
  poolId: PoolId,
  lp: AssetAmount,
  x: AssetAmount,
  y: AssetAmount,
  feeNum: Int,
  box: BoxInfo
) {

  def supplyLP: Long = constants.cfmm.TotalEmissionLP - lp.value

  def deposit(inX: AssetAmount, inY: AssetAmount, nextBox: BoxInfo): CfmmPool = {
    val unlocked = math.min(
      inX.value * supplyLP / x.value,
      inY.value * supplyLP / y.value
    )
    copy(lp = lp - unlocked, x = x + inX, y = y + inY, box = nextBox)
  }

  def redeem(inLp: AssetAmount, nextBox: BoxInfo): CfmmPool = {
    val redeemedX = inLp.value * x.value / supplyLP
    val redeemedY = inLp.value * y.value / supplyLP
    copy(lp = lp + inLp, x = x - redeemedX, y = y - redeemedY, box = nextBox)
  }

  def swap(in: AssetAmount, nextBox: BoxInfo): CfmmPool = {
    val (deltaX, deltaY) =
      if (in.id == x.id)
        (in.value, -y.value * in.value * feeNum / (x.value * constants.cfmm.FeeDenominator + in.value * feeNum))
      else
        (-x.value * in.value * feeNum / (y.value * constants.cfmm.FeeDenominator + in.value * feeNum), in.value)
    copy(x = x + deltaX, y = y + deltaY, box = nextBox)
  }

  def rewardLP(inX: AssetAmount, inY: AssetAmount): AssetAmount =
    lp.withAmount(
      math.min(
        (BigInt(inX.value) * supplyLP / x.value).toLong,
        (BigInt(inY.value) * supplyLP / y.value).toLong
      )
    )

  def shares(lp: AssetAmount): (AssetAmount, AssetAmount) =
    x.withAmount(BigInt(lp.value) * x.value / supplyLP) ->
    y.withAmount(BigInt(lp.value) * y.value / supplyLP)

  def outputAmount(input: AssetAmount): AssetAmount = {
    def out(in: AssetAmount, out: AssetAmount) =
      out.withAmount(
        BigInt(out.value) * input.value * feeNum /
        (in.value * constants.cfmm.FeeDenominator + input.value * feeNum)
      )
    if (input.id == x.id) out(x, y) else out(y, x)
  }
}
