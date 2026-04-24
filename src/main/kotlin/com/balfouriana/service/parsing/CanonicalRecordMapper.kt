package com.balfouriana.service.parsing

import com.balfouriana.domain.CanonicalRecordType
import com.balfouriana.domain.ParseRejectionCode
import org.springframework.stereotype.Component

@Component
class CanonicalRecordMapper {
    fun map(record: ParsedStructuredRecord): MappingOutcome {
        val type = record.typeHint ?: return MappingOutcome.Rejected(
            ParserRejection(
                recordIndex = record.recordIndex,
                code = ParseRejectionCode.MAPPING_FAILED,
                reason = "Missing canonical type hint",
                rawRecord = record.rawRecord
            )
        )
        val normalized = when (type) {
            CanonicalRecordType.TRADE -> mapTrade(record.fields)
            CanonicalRecordType.POSITION -> mapPosition(record.fields)
            CanonicalRecordType.CASH_MOVEMENT -> mapCashMovement(record.fields)
            CanonicalRecordType.CORPORATE_ACTION -> mapCorporateAction(record.fields)
        }
        return MappingOutcome.Mapped(type, normalized)
    }

    private fun mapTrade(fields: Map<String, String>): Map<String, String> = mapOf(
        "record_type" to "TRADE",
        "trade_id" to fields["trade_id"].orEmpty(),
        "instrument_id" to fields["instrument_id"].orEmpty(),
        "trade_date" to fields["trade_date"].orEmpty(),
        "quantity" to fields["quantity"].orEmpty(),
        "price" to fields["price"].orEmpty(),
        "currency" to fields["currency"].orEmpty(),
        "venue" to fields["venue"].orEmpty()
    )

    private fun mapPosition(fields: Map<String, String>): Map<String, String> = mapOf(
        "record_type" to "POSITION",
        "position_id" to fields["position_id"].orEmpty(),
        "instrument_id" to fields["instrument_id"].orEmpty(),
        "position_date" to fields["position_date"].orEmpty(),
        "quantity" to fields["quantity"].orEmpty(),
        "valuation" to fields["valuation"].orEmpty(),
        "currency" to fields["currency"].orEmpty()
    )

    private fun mapCashMovement(fields: Map<String, String>): Map<String, String> = mapOf(
        "record_type" to "CASH_MOVEMENT",
        "cash_id" to fields["cash_id"].orEmpty(),
        "currency" to fields["currency"].orEmpty(),
        "amount" to fields["amount"].orEmpty(),
        "value_date" to fields["value_date"].orEmpty(),
        "counterparty_lei" to fields["counterparty_lei"].orEmpty()
    )

    private fun mapCorporateAction(fields: Map<String, String>): Map<String, String> = mapOf(
        "record_type" to "CORPORATE_ACTION",
        "action_id" to fields["action_id"].orEmpty(),
        "instrument_id" to fields["instrument_id"].orEmpty(),
        "action_type" to fields["action_type"].orEmpty(),
        "effective_date" to fields["effective_date"].orEmpty(),
        "ratio" to fields["ratio"].orEmpty()
    )
}

sealed interface MappingOutcome {
    data class Mapped(val recordType: CanonicalRecordType, val canonicalFields: Map<String, String>) : MappingOutcome
    data class Rejected(val rejection: ParserRejection) : MappingOutcome
}
