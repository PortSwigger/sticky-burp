package com.ganggreentempertatum.stickyburp

import burp.api.montoya.http.handler.*
import burp.api.montoya.http.message.requests.HttpRequest

class StickyBurpHttpHandler(private val tab: StickyBurpTab) : HttpHandler {
    override fun handleHttpRequestToBeSent(requestToBeSent: HttpRequestToBeSent): RequestToBeSentAction {
        var modifiedRequest = requestToBeSent.toString()

        for (variable in tab.getVariables()) {
            modifiedRequest = modifiedRequest.replace("\${${variable.name}}", variable.value)
        }

        return if (modifiedRequest != requestToBeSent.toString()) {
            RequestToBeSentAction.continueWith(HttpRequest.httpRequest(
                requestToBeSent.httpService(),
                modifiedRequest
            ))
        } else {
            RequestToBeSentAction.continueWith(requestToBeSent)
        }
    }

    override fun handleHttpResponseReceived(responseReceived: HttpResponseReceived): ResponseReceivedAction {
        return ResponseReceivedAction.continueWith(responseReceived)
    }
}
