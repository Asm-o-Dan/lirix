package com.example.npc.pipeline.dsl.codec

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.TransactionStatus
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CategorySerializer : KSerializer<Category> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Category", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Category) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Category = Category.valueOf(decoder.decodeString())
}

object EngineSerializer : KSerializer<Engine> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Engine", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Engine) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Engine = Engine.valueOf(decoder.decodeString())
}

object DirectionSerializer : KSerializer<Direction> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Direction", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Direction) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Direction = Direction.valueOf(decoder.decodeString())
}

object TransactionStatusSerializer : KSerializer<TransactionStatus> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TransactionStatus", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TransactionStatus) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): TransactionStatus = TransactionStatus.valueOf(decoder.decodeString())
}
