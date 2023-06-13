package org.ergoplatform.uexplorer.utxo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{ByteBufferOutput, Input}
import com.esotericsoftware.kryo.serializers.DefaultSerializers.CollectionsSingletonSetSerializer
import com.esotericsoftware.kryo.serializers.ImmutableCollectionsSerializers.JdkImmutableSetSerializer
import com.esotericsoftware.kryo.serializers.{ImmutableCollectionsSerializers, MapSerializer}
import com.esotericsoftware.kryo.util.Pool
import org.ergoplatform.uexplorer.db.{BlockInfo, DbCodec}
import org.ergoplatform.uexplorer.{Address, BlockId, BlockMetadata, Height}

import java.nio.ByteBuffer
import java.util
import scala.util.Try

object BlockIdsCodec extends DbCodec[java.util.Set[BlockId]] {
  override def read(bytes: Array[Byte]): java.util.Set[BlockId] = {
    val input = new Input(bytes)
    val kryo  = KryoSerialization.pool.obtain()
    try kryo.readObject(input, classOf[util.HashSet[BlockId]])
    finally {
      KryoSerialization.pool.free(kryo)
      input.close()
    }
  }

  override def write(blockIds: java.util.Set[BlockId]): Array[Byte] = {
    val buffer = ByteBuffer.allocate(blockIds.size() * 128)
    val output = new ByteBufferOutput(buffer)
    val kryo   = KryoSerialization.pool.obtain()
    try kryo.writeObject(output, blockIds)
    finally {
      KryoSerialization.pool.free(kryo)
      output.close()
    }
    buffer.array()
  }
}