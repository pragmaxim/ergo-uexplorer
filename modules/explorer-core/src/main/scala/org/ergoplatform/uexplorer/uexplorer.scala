package org.ergoplatform

import eu.timepit.refined.api.RefType.tagRefType.unsafeWrap
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.{HexStringSpec, MatchesRegex, ValidByte}
import eu.timepit.refined.refineV
import io.circe.*
import org.ergoplatform.uexplorer.{BoxCount, LastHeight, TxCount}
import scorex.crypto.hash.Digest32

import scala.collection.mutable
import scala.collection.compat.immutable.ArraySeq
import scala.collection.immutable.{ArraySeq, TreeMap}
import scala.collection.immutable.ListMap
import scala.util.Try

package object uexplorer {

  type Value     = Long
  type Height    = Int
  type Timestamp = Long

  type MinerReward = Long
  type MinerFee    = Long

  type BoxCount   = Int
  type TxCount    = Int
  type LastHeight = Int

  type Revision = Long

  type Base58Spec          = MatchesRegex["[1-9A-HJ-NP-Za-km-z]+"]
  type Address             = String Refined Base58Spec
  type ErgoTreeHex         = String Refined HexStringSpec
  type AdProofsRootHex     = String Refined HexStringSpec
  type AvlTreePathProofHex = String Refined HexStringSpec
  type TreeRootHashHex     = String Refined HexStringSpec
  type ExtensionDigestHex  = String Refined HexStringSpec
  type BoxRegisterValueHex = String Refined HexStringSpec
  type StateRootHex        = String Refined HexStringSpec
  type TransactionsRootHex = String Refined HexStringSpec
  type PowHex              = String Refined HexStringSpec
  type PowNonceHex         = String Refined HexStringSpec
  type InputProofHex       = String Refined HexStringSpec
  type NetworkPrefix       = String Refined ValidByte

  object Address {
    import eu.timepit.refined.auto.autoUnwrap
    case class State(value: Value)
    extension (x: Address) def unwrapped: String = x
    def fromStringUnsafe(s: String): Address     = unsafeWrap(refineV[Base58Spec].unsafeFrom(s))
  }

  object ErgoTreeHex {
    import eu.timepit.refined.auto.autoUnwrap
    case class State(value: Value)
    extension (x: ErgoTreeHex) def unwrapped: String = x
    def fromStringUnsafe(s: String): ErgoTreeHex     = unsafeWrap(refineV[HexStringSpec].unsafeFrom(s))
  }

  object NetworkPrefix {
    def fromStringUnsafe(s: String): NetworkPrefix = unsafeWrap(refineV[ValidByte].unsafeFrom(s))
  }

  type HexString = String Refined HexStringSpec

  object HexString {
    def fromStringUnsafe(s: String): HexString = unsafeWrap(refineV[HexStringSpec].unsafeFrom(s))
  }

  object AvlTreePathProofHex {
    def fromStringUnsafe(s: String): AvlTreePathProofHex = unsafeWrap(refineV[HexStringSpec].unsafeFrom(s))
  }

  type BlockId = String Refined HexStringSpec

  object BlockId {
    def fromStringUnsafe(s: String): BlockId = unsafeWrap(HexString.fromStringUnsafe(s))
  }

  type TokenId = String Refined HexStringSpec

  object TokenId {
    def fromStringUnsafe(s: String): TokenId = unsafeWrap(HexString.fromStringUnsafe(s))
  }

  type TemplateHashHex = String Refined HexStringSpec
  object TemplateHashHex {
    def fromStringUnsafe(s: String): TemplateHashHex = unsafeWrap(HexString.fromStringUnsafe(s))
  }

  opaque type TxId = String

  object TxId {
    def apply(s: String): TxId = s

    given Encoder[TxId] = Encoder.encodeString

    given Decoder[TxId]                       = Decoder.decodeString
    extension (x: TxId) def unwrapped: String = x
  }

  opaque type BoxId = String

  object BoxId {
    def apply(s: String): BoxId = s

    given Encoder[BoxId] = Encoder.encodeString

    given Decoder[BoxId]                       = Decoder.decodeString
    extension (x: BoxId) def unwrapped: String = x
  }

  opaque type TokenName = String

  object TokenName {
    def apply(s: String): TokenName = s

    given Encoder[TokenName] = Encoder.encodeString

    given Decoder[TokenName] = Decoder.decodeString

    extension (x: TokenName) def unwrapped: String = x
  }

  opaque type TokenSymbol = String

  object TokenSymbol {
    def apply(s: String): TokenSymbol = s

    given Encoder[TokenSymbol] = Encoder.encodeString

    given Decoder[TokenSymbol] = Decoder.decodeString

    extension (x: TokenSymbol) def unwrapped: String = x
  }

  opaque type TokenType = String

  object TokenType {
    def apply(s: String): TokenType = s

    val Eip004: TokenType = TokenType("EIP-004")

    given Encoder[TokenType] = Encoder.encodeString

    given Decoder[TokenType] = Decoder.decodeString

    extension (x: TokenType) def unwrapped: String = x

  }

  enum RegisterId {
    case R0
    case R1
    case R2
    case R3
    case R4
    case R5
    case R6
    case R7
    case R8
    case R9
  }

  object RegisterId {
    given keyEncoder: KeyEncoder[RegisterId] = (a: RegisterId) => a.toString
    given keyDecoder: KeyDecoder[RegisterId] = KeyDecoder.decodeKeyString.map(RegisterId.valueOf)
  }

  implicit class MapPimp[K, V](underlying: Map[K, V]) {

    def putOrRemove(k: K)(f: Option[V] => Option[V]): Map[K, V] =
      f(underlying.get(k)) match {
        case None    => underlying removed k
        case Some(v) => underlying updated (k, v)
      }

    def adjust(k: K)(f: Option[V] => V): Map[K, V] = underlying.updated(k, f(underlying.get(k)))
  }

  implicit class MutableMapPimp[K, V](underlying: mutable.Map[K, V]) {

    def putOrRemove(k: K)(f: Option[V] => Option[V]): mutable.Map[K, V] =
      f(underlying.get(k)) match {
        case None => underlying -= k
        case Some(v) =>
          underlying.put(k, v)
          underlying
      }

    def adjust(k: K)(f: Option[V] => V): mutable.Map[K, V] = {
      underlying.put(k, f(underlying.get(k)))
      underlying
    }
  }

}
