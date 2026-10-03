package com.example.npc.core.model.finance

import java.text.Normalizer
import java.util.Locale

/** Evidence shared by live extraction, the VM, and learned templates. UNKNOWN is not an expense. */
data class DirectionResolution(
    val type: TransactionType = TransactionType.UNKNOWN,
    val isRefund: Boolean = false,
    val isDeclined: Boolean = false,
    val isSuppressed: Boolean = false,
    val confidence: Float = 0f,
    val reason: String = "No direction evidence"
)

/**
 * Bank-independent, conservative direction classification. Amount/currency extraction is separate.
 * Never infer direction from a bank name, an unsigned amount, balance, emoji, or a generic transfer.
 * Fixed, bounded expressions are used only on bounded notification text (no user-supplied regex).
 */
object TransactionDirectionResolver {
    private const val MAX_TEXT_LENGTH = 4096
    private fun words(pattern: String) = Regex("(?<![\\p{L}])(?:$pattern)(?![\\p{L}])")
    private val credit = words("зачисл[а-я]*|поступил[а-я]*|поступлен[а-я]*|пополнен[а-я]*|пополнили|внесен[а-я]*|внесени[а-я]*|зарплат[а-я]*|пришли\\s+деньги|деньги\\s+пришли|получен[а-я]*|credited|received|deposit(?:ed)?|salary|top[ -]?up|money\\s+(?:in|received)|alimentar[a-z]*|incasar[a-z]*|depuner[a-z]*|salariu|popolnen[a-z]*|zachisl[a-z]*|postupl[a-z]*")
    private val debit = words("списан[а-я]*|списал[а-я]*|покупк[а-я]*|оплат[а-я]*|платеж[а-я]*|сняти[а-я]*|снято|резервирован[а-я]*|debited|purchase(?:d)?|payment|paid|spent|withdrawal|withdrawn|plata|achitar[a-z]*|achizit[a-z]*|cumparatur[a-z]*|extragere|cheltuieli|pokupk[a-z]*|oplat[a-z]*|spisan[a-z]*")
    private val refund = words("возврат[а-я]*|возмещ[а-я]*|сторнир[а-я]*|отмена\\s+операции|refund(?:ed)?|reversal|reversed|restitu[a-z]*|ramburs[a-z]*|retur|storn[a-z]*|vozvrat[a-z]*")
    private val transfer = words("перевод[а-я]*|перечислен[а-я]*|transfer[a-z]*|p2p|remittance|perevod[a-z]*")
    private val incoming = words("входящ[а-я]*|incoming|inbound|primit[a-z]*")
    private val arrival = words("зачисл[а-я]*|поступил[а-я]*|получен[а-я]*|received|credited|incasat[a-z]*|primit[a-z]*")
    private val outgoing = words("исходящ[а-я]*|отправлен[а-я]*|отправили|отправлено|outgoing|outbound|sent|trimis[a-z]*")
    private val ownTransfer = words("между\\s+(?:своими|моими)\\s+счетами|на\\s+свой\\s+счет|between\\s+(?:your|own|my)\\s+accounts|intre\\s+conturile")
    private val cashback = words("cash\\s*back|к[эе]шб[эе]к[а-я]*|кешбек[а-я]*")
    private val cashbackPayment = words("cash\\s*back\\s+(?:paid|credited|received)|к[эе]шб[эе]к[а-я]*\\s+(?:начисл[а-я]*|выплачен[а-я]*)")
    private val failure = words("отклон[а-я]*|отказ[а-я]*|неуспеш[а-я]*|недостаточ[а-я]*|declin[a-z]*|reject[a-z]*|failed|failure|cancelled|canceled|отменен[а-я]*|anulat[a-z]*|insufficient|refuz[a-z]*|respins[a-z]*|esuata|не\\s+(?:прош[а-я]*|выполн[а-я]*|удалось)|not\\s+(?:successful|completed)")
    private val negatedCredit = words("не\\s+(?:был[а-я]*\\s+)?(?:зачисл[а-я]*|поступил[а-я]*|получен[а-я]*|возвращен[а-я]*|списан[а-я]*|оплачен[а-я]*|начисл[а-я]*|приш[а-я]*|обработан[а-я]*)|(?:not|never)\\s+(?:(?:yet|been|be|actually)\\s+){0,3}(?:credited|received|refunded|debited|paid|processed|executed)|nu\\s+(?:a\\s+fost\\s+)?(?:incasat|primit)")
    private val pending = words("будет|будут|ожида[а-я]*|ожидан[а-я]*|в\\s+обработке|заявка|запрос\\s+на|предстоит|планиру[а-я]*|pending|processing|scheduled|expected|will|requested|request|in\\s+asteptare")
    private val promotion = words("получите|получай[а-я]*|заработайте|оформите|предлага[а-я]*|покупайте|акци[а-я]*|скидк[а-я]*|промокод|до\\s+\\d|up\\s+to|offer|discount|promo[a-z]*|earn|you\\s+can|get\\s+cash|oferta|reducere")
    private val otp = words("парол[а-я]*|пин(?:[ -]код)?|код\\s+(?:подтверждения|для|операции)|код:\\s*\\d|otp|password|verification\\s+code|confirmation\\s+code|one[ -]time|cod\\s+(?:de\\s+)?confirmare")
    private val balance = words("баланс[а-я]*|остат[а-я]*|доступн[а-я]*|balance|available|sold|disponibil|limit|лимит[а-я]*")
    private val currency = "(?:rup|rub|mdl|usd|eur|ron|uah|gbp|руб[а-я.]*|лей|лея|lei|[₽$€£])"
    private val amount = "[0-9]+(?:[ ,.][0-9]+)*"
    private val signedAmount = Regex("(?<![\\p{L}\\d])([+−-])\\s*(?:$currency\\s*$amount|$amount\\s*$currency)(?![\\p{L}])")
    private val monetaryCashback = Regex("(?:cash\\s*back|к[эе]шб[эе]к[а-я]*|кешбек[а-я]*)\\s*[:+]?\\s*$amount\\s*$currency(?![\\p{L}])")
    private val refundObject = words("(?:за|of|for)\\s+(?:покупк[а-я]*|оплат[а-я]*|платеж[а-я]*|purchase|payment)|(?:возврат[а-я]*|refund|restituire|rambursare)\\s+(?:покупк[а-я]*|оплат[а-я]*|платеж[а-я]*|purchase|payment|plata)")

    fun resolve(title: String? = null, body: String, explicitType: TransactionType? = null): DirectionResolution {
        if ((title?.length ?: 0) + body.length > MAX_TEXT_LENGTH) {
            return DirectionResolution(isSuppressed = true, reason = "Notification exceeds parsing limit")
        }
        val titleText = normalize(title.orEmpty())
        val bodyText = normalize(body)
        val text = "$titleText\n$bodyText"
        if (otp.containsMatchIn(text)) return DirectionResolution(isSuppressed = true, reason = "Authentication message")
        if (negatedCredit.containsMatchIn(text)) {
            return DirectionResolution(isSuppressed = true, reason = "Operation explicitly negated")
        }
        // Failure is evaluated before advice such as 'top up and retry' in the same notification.
        val declined = failure.containsMatchIn(text)
        val firstOperation = listOfNotNull(credit.find(text), debit.find(text), refund.find(text))
            .minOfOrNull { it.range.first } ?: Int.MAX_VALUE
        val percentageOffer = '%' in text &&
            (firstOperation == Int.MAX_VALUE || (cashback.find(text)?.range?.first ?: Int.MAX_VALUE) < firstOperation)
        if (!declined && (pending.containsMatchIn(text) || promotion.containsMatchIn(text) || percentageOffer)) {
            return DirectionResolution(isSuppressed = true, reason = "Promised, pending, or promotional operation")
        }
        val titleEvidence = evidence(titleText)
        val bodyEvidence = evidence(bodyText)
        val combined = evidence(text)
        if (declined) {
            // Keep failed attempts visible, but never make a failed refund/cashback a completed credit.
            return DirectionResolution(
                type = if (debit.containsMatchIn(text)) TransactionType.DEBIT else TransactionType.UNKNOWN,
                isDeclined = true, confidence = 0.98f, reason = "Operation declined"
            )
        }
        if (titleEvidence.conflict || bodyEvidence.conflict ||
            (titleEvidence.type.isDirected() && bodyEvidence.type.isDirected() && titleEvidence.type != bodyEvidence.type)) {
            return DirectionResolution(reason = "Conflicting direction evidence")
        }
        if (combined.conflict) return DirectionResolution(reason = "Conflicting direction evidence")
        val inferred = combined.type
        val explicit = explicitType?.takeUnless { it == TransactionType.UNKNOWN }
        if (explicit != null && explicit != inferred &&
            (inferred.isDirected() || ownTransfer.containsMatchIn(text))) {
            return DirectionResolution(reason = "Template direction conflicts with notification")
        }
        val resolved = explicit ?: inferred
        return DirectionResolution(
            type = resolved,
            isRefund = combined.refund && resolved == TransactionType.CREDIT,
            confidence = when {
                resolved == TransactionType.UNKNOWN -> 0f
                explicit != null -> 0.98f
                combined.refund -> 0.96f
                combined.signOnly -> 0.70f
                else -> 0.92f
            },
            reason = when {
                explicit != null -> "Explicit template direction"
                resolved == TransactionType.UNKNOWN -> "No direction evidence"
                else -> "Notification direction evidence"
            }
        )
    }

    private data class Evidence(
        val type: TransactionType = TransactionType.UNKNOWN,
        val refund: Boolean = false,
        val conflict: Boolean = false,
        val signOnly: Boolean = false
    )

    private fun evidence(text: String): Evidence {
        if (ownTransfer.containsMatchIn(text)) return Evidence(TransactionType.TRANSFER)
        val hasRefund = refund.containsMatchIn(text)
        val hasTransfer = transfer.containsMatchIn(text)
        // A bare cash-back amount is a statement; a bare 'cashback' slogan is not.
        val hasCashback = cashback.containsMatchIn(text) &&
            (credit.containsMatchIn(text) || monetaryCashback.containsMatchIn(text) || cashbackPayment.containsMatchIn(text))
        val creditSignal = (credit.containsMatchIn(text) && (!hasTransfer || arrival.containsMatchIn(text))) || hasCashback ||
            (hasTransfer && incoming.containsMatchIn(text))
        val debitText = cashbackPayment.replace(if (hasRefund) refundObject.replace(text, "") else text, "")
        val debitSignal = debit.containsMatchIn(debitText) || (hasTransfer && outgoing.containsMatchIn(text))
        var plus = false
        var minus = false
        for (match in signedAmount.findAll(text)) {
            // A balance sign, card separator, date, phone or transaction ID is not direction.
            val before = text.substring(maxOf(0, match.range.first - 40), match.range.first)
                .substringAfterLast('\n').substringAfterLast(';')
            if (balance.containsMatchIn(before) || before.lastOrNull()?.isLetterOrDigit() == true) continue
            if (match.groupValues[1] == "+") plus = true else minus = true
        }
        val incomingMoney = creditSignal || hasRefund || plus
        val outgoingMoney = debitSignal || minus
        if (incomingMoney && outgoingMoney) return Evidence(conflict = true)
        return Evidence(
            type = when {
                incomingMoney -> TransactionType.CREDIT
                outgoingMoney -> TransactionType.DEBIT
                hasTransfer -> TransactionType.TRANSFER
                else -> TransactionType.UNKNOWN
            },
            refund = hasRefund,
            signOnly = (plus || minus) && !creditSignal && !debitSignal && !hasRefund
        )
    }

    private fun TransactionType.isDirected() = this == TransactionType.CREDIT || this == TransactionType.DEBIT

    private fun normalize(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("(hasn|haven|wasn|weren|isn|aren|didn|doesn|don)['’]t"), "not")
        .replace('ё', 'е').replace('ă', 'a').replace('â', 'a').replace('î', 'i')
        .replace('ș', 's').replace('ş', 's').replace('ț', 't').replace('ţ', 't')
        .filterNot { Character.getType(it) == Character.FORMAT.toInt() }
        .replace(Regex("[\\p{Zs}\\t]+"), " ")
}
