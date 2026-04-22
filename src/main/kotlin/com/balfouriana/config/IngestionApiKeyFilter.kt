package com.balfouriana.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
class IngestionApiKeyFilter(
    private val ingestionProperties: IngestionProperties
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!request.method.equals("POST", ignoreCase = true)) return true
        val uri = request.requestURI
        if (uri != "/ingest" && !uri.endsWith("/ingest")) return true
        val configured = ingestionProperties.rest.apiKey
        return configured.isNullOrBlank()
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val expected = ingestionProperties.rest.apiKey!!
        val provided = request.getHeader(INGESTION_API_KEY_HEADER)
        if (provided != expected) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.writer.write("unauthorized")
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val INGESTION_API_KEY_HEADER = "X-Ingestion-Api-Key"
    }
}
