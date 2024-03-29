package org.ergoplatform.uexplorer.parser

import com.google.bitcoin.core.Base58
import eu.timepit.refined.auto.*
import io.circe.{Decoder, DecodingFailure}
import org.ergoplatform.uexplorer.{Address, ErgoTreeHash, ErgoTreeHex, ErgoTreeT8Hash, ErgoTreeT8Hex}
import org.ergoplatform.*
import scorex.crypto.hash.{Blake2b256, Digest32, Sha256}
import scorex.util.encode.Base16
import sigmastate.*
import sigmastate.Values.{ErgoTree, FalseLeaf, SigmaPropConstant, Value}
import sigmastate.basics.DLogProtocol.ProveDlogProp
import sigmastate.serialization.{ErgoTreeSerializer, SigmaSerializer}
import zio.{Task, ZIO}

import scala.util.Try

object ErgoTreeParser {

  private val treeSerializer: ErgoTreeSerializer = ErgoTreeSerializer.DefaultSerializer

  def ergoTreeHex2ErgoTree(ergoTree: ErgoTreeHex): Try[Values.ErgoTree] =
    Base16.decode(ergoTree).map(treeSerializer.deserializeErgoTree)

  def isErgoTreeT8(ergoTreeBytes: Array[Byte]): Boolean = {
    val (_, _, constants, _) = treeSerializer.deserializeHeaderWithTreeBytes(SigmaSerializer.startReader(ergoTreeBytes))
    constants.nonEmpty
  }

  def ergoTreeHex2Hash(ergoTreeHex: ErgoTreeHex): Task[ErgoTreeHash] = ZIO.fromTry(
    ergoTreeHex2ErgoTree(ergoTreeHex).map { ergoTree =>
      ErgoTreeHash.fromStringUnsafe(Base16.encode(Sha256.hash(ergoTree.bytes)))
    }
  )

  @inline def ergoTreeHex2T8Hex(
    ergoTree: ErgoTreeHex
  )(implicit enc: ErgoAddressEncoder): Task[Option[ErgoTreeT8Hex]] =
    ZIO.fromTry(Base16.decode(ergoTree)).map {
      case bytes if isErgoTreeT8(bytes) =>
        val tree = treeSerializer.deserializeErgoTree(bytes)
        val t8Opt =
          tree.root match {
            case Right(SigmaPropConstant(ProveDlogProp(_))) =>
              None
            case Right(enc.IsPay2SHAddress(_)) =>
              Option(tree.template)
            case Right(b: Value[SSigmaProp.type] @unchecked) if b.tpe == SSigmaProp =>
              Option(tree.template)
            case _ =>
              None
          }
        t8Opt.map(t => ErgoTreeT8Hex.fromStringUnsafe(Base16.encode(t)))
      case _ =>
        Option.empty[ErgoTreeT8Hex]
    }

  @inline def ergoTreeHex2T8(
    ergoTree: ErgoTreeHex
  )(implicit enc: ErgoAddressEncoder): Try[(ErgoTreeHash, Option[(ErgoTreeT8Hex, ErgoTreeT8Hash)])] =
    Base16.decode(ergoTree).map {
      case bytes if isErgoTreeT8(bytes) =>
        val tree = treeSerializer.deserializeErgoTree(bytes)
        val t8Opt =
          tree.root match {
            case Right(SigmaPropConstant(ProveDlogProp(_))) =>
              None
            case Right(enc.IsPay2SHAddress(_)) =>
              Option(tree.template)
            case Right(b: Value[SSigmaProp.type] @unchecked) if b.tpe == SSigmaProp =>
              Option(tree.template)
            case _ =>
              None
          }
        ErgoTreeHash.fromStringUnsafe(Base16.encode(Sha256.hash(bytes))) ->
        t8Opt.map { t =>
          ErgoTreeT8Hex.fromStringUnsafe(Base16.encode(t)) -> ErgoTreeT8Hash.fromStringUnsafe(Base16.encode(Sha256.hash(t)))
        }
      case bytes =>
        ErgoTreeHash.fromStringUnsafe(Base16.encode(Sha256.hash(bytes))) -> Option.empty[(ErgoTreeT8Hex, ErgoTreeT8Hash)]
    }

  // http://213.239.193.208:9053/blocks/at/545684
  // http://213.239.193.208:9053/blocks/2ad5af788bfd1b92790eadb42a300ad4fc38aaaba599a43574b1ea45d5d9dee4
  // http://213.239.193.208:9053/utils/ergoTreeToAddress/cd07021a8e6f59fd4a
  // Note that some ErgoTree can be invalid
  @inline def ergoTreeHex2ErgoAddress(ergoTreeHex: ErgoTreeHex)(implicit enc: ErgoAddressEncoder): ErgoAddress =
    ergoTreeHex2ErgoTree(ergoTreeHex)
      .flatMap(enc.fromProposition)
      .getOrElse(Pay2SAddress(FalseLeaf.toSigmaProp): ErgoAddress)

  @inline def ergoAddress2Base58Address(address: ErgoAddress)(implicit enc: ErgoAddressEncoder): Task[Address] =
    ZIO.attempt {
      val withNetworkByte = (enc.networkPrefix + address.addressTypePrefix).toByte +: address.contentBytes
      val checksum        = ErgoAddressEncoder.hash256(withNetworkByte).take(ErgoAddressEncoder.ChecksumLength)
      // avoiding Address.fromStringUnsafe, as Base58 produced valid result for all Ergo addresses so far
      Base58.encode(withNetworkByte ++ checksum).asInstanceOf[Address]
    }

  @inline def base58Address2ErgoTreeHex(address: Address)(implicit enc: ErgoAddressEncoder): Task[ErgoTreeHex] =
    base58Address2ErgoTree(address).map { ergoTree =>
      ErgoTreeHex.fromStringUnsafe(Base16.encode(ergoTree.bytes))
    }

  @inline def base58Address2ErgoTreeHash(address: Address)(implicit enc: ErgoAddressEncoder): Task[ErgoTreeHash] =
    base58Address2ErgoTree(address).map { ergoTree =>
      ErgoTreeHash.fromStringUnsafe(Base16.encode(Sha256.hash(ergoTree.bytes)))
    }

  @inline def base58Address2ErgoTreeT8Hex(
    address: Address
  )(implicit enc: ErgoAddressEncoder): Task[ErgoTreeT8Hex] =
    base58Address2ErgoTree(address).map { ergoTree =>
      ErgoTreeT8Hex.fromStringUnsafe(Base16.encode(ergoTree.template))
    }

  @inline def base58Address2ErgoTreeT8Hash(address: Address)(implicit enc: ErgoAddressEncoder): Task[ErgoTreeT8Hash] =
    base58Address2ErgoTree(address).map { ergoTree =>
      ErgoTreeT8Hex.fromStringUnsafe(Base16.encode(Sha256.hash(ergoTree.template)))
    }

  @inline def base58Address2ErgoTree(address: Address)(implicit enc: ErgoAddressEncoder): Task[ErgoTree] =
    ZIO.fromTry(enc.fromString(address).map(_.script))

  @inline def ergoTreeHex2Base58Address(ergoTreeHex: ErgoTreeHex)(implicit enc: ErgoAddressEncoder): Task[Address] =
    ergoAddress2Base58Address(ergoTreeHex2ErgoAddress(ergoTreeHex))

  def base58AddressToErgoAddress(address: Address)(implicit enc: ErgoAddressEncoder): Task[ErgoAddress] =
    ZIO.fromTry(enc.fromString(address))

  def ergoTreeToHex(ergoTree: ErgoTree): ErgoTreeHex = ErgoTreeHex.fromStringUnsafe(Base16.encode(ergoTree.bytes))
}
