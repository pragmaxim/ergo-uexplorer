package org.ergoplatform.uexplorer.node

import io.circe.Json
import org.ergoplatform.uexplorer.*

import scala.collection.immutable.ArraySeq
import scala.util.{Failure, Success, Try}

final case class ApiAdProof(
  headerId: BlockId,
  proofBytes: AvlTreePathProofHex,
  digest: TreeRootHashHex
)

final case class ApiAsset(
  tokenId: TokenId,
  amount: Amount
)

final case class ApiBlockExtension(
  headerId: BlockId,
  digest: ExtensionDigestHex,
  fields: Json
)

final case class ApiBlockTransactions(
  headerId: BlockId,
  transactions: ArraySeq[ApiTransaction]
)

final case class ApiDataInput(boxId: BoxId)

final case class ApiDifficulty(value: BigDecimal)

final case class ApiFullBlock(
  header: ApiHeader,
  transactions: ApiBlockTransactions,
  extension: ApiBlockExtension,
  adProofs: Option[ApiAdProof],
  size: Int
)

final case class ApiHeader(
  id: BlockId,
  parentId: BlockId,
  version: Byte,
  height: Height,
  nBits: Long,
  difficulty: ApiDifficulty,
  timestamp: Timestamp,
  stateRoot: StateRootHex,
  adProofsRoot: AdProofsRootHex,
  transactionsRoot: TransactionsRootHex,
  extensionHash: ExtensionDigestHex,
  minerPk: ErgoTreeHex,
  w: PowHex,
  n: PowNonceHex,
  d: BigInt,
  votes: String
)

final case class ApiOutput(
  boxId: BoxId,
  value: Value,
  creationHeight: CreationHeight,
  ergoTree: ErgoTreeHex,
  assets: List[ApiAsset],
  additionalRegisters: Map[RegisterId, BoxRegisterValueHex]
)

final case class ApiPowSolutions(pk: ErgoTreeHex, w: PowHex, n: PowNonceHex, d: BigInt)

final case class ApiSpendingProof(proofBytes: Option[AvlTreePathProofHex], extension: Json)

final case class ApiInput(boxId: BoxId, spendingProof: ApiSpendingProof)

final case class ApiTransaction(
  id: TxId,
  inputs: ArraySeq[ApiInput],
  dataInputs: List[ApiDataInput],
  outputs: ArraySeq[ApiOutput],
  size: Option[Int]
)

final case class ExpandedRegister(
  serializedValue: BoxRegisterValueHex,
  regValue: Option[RegisterValue]
)

final case class RegisterValue(sigmaType: SigmaType, value: String)

final case class TokenProps(
  name: String,
  description: String,
  decimals: Int
)
