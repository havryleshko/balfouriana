package com.balfouriana.service.filing.template

import com.balfouriana.domain.FilingTemplateVersion
import java.time.Instant

object MifidFilingTemplatePack {
    const val SCHEMA_RESOURCE_PATH = "filing/mifid/schemas/mifir-transaction-report-2026.05.18.xsd"
    const val SCHEMA_VERSION = "2026.05.18"

    val version: FilingTemplateVersion = FilingTemplateVersion(
        templateId = "step4-mifid-xml",
        version = "2026.05.18",
        effectiveFrom = Instant.parse("2026-05-18T00:00:00Z"),
        authority = "FCA",
        authorityRef = "MiFIR RTS 22 transaction reporting",
        publishedAt = "2026-04-19"
    )

    val canonicalToXmlElement: Map<String, String> = linkedMapOf(
        "trade_id" to "TransactionReferenceNumber",
        "instrument_id" to "InstrumentIdentification",
        "trade_date" to "TradingDateTime",
        "quantity" to "Quantity",
        "price" to "Price",
        "currency" to "TradingCurrency",
        "buyer_lei" to "BuyerIdentificationCode",
        "seller_lei" to "SellerIdentificationCode",
        "decision_maker_lei" to "InvestmentDecisionWithinFirm",
        "execution_actor_type" to "ExecutionWithinFirm",
        "venue_code" to "TradingVenue",
        "otc_indicator" to "OTCPostTradeIndicator",
        "waiver_indicator" to "WaiverIndicator",
        "short_selling_indicator" to "ShortSellingIndicator",
        "commodity_derivative_indicator" to "CommodityDerivativeIndicator",
        "price_notation" to "PriceNotation",
        "price_currency" to "PriceCurrency",
        "calculated_notional" to "NotionalAmount",
        "mifid_transaction_side" to "TransactionSide",
        "mifid_execution_mode" to "ExecutionMode"
    )
}
