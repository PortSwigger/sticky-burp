package com.ganggreentempertatum.stickyburp

import kotlinx.serialization.Serializable

@Serializable
data class StickyVariable(
    val name: String,
    val value: String,
    val source: String,
    val notes: String = ""
)
