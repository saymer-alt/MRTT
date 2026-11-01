// SPDX-FileCopyrightText: 2026 amurcanov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.csqtt.client

internal object WorkerCountPolicy {
    fun normalize(
        requested: Int,
        maximum: Int = MrttConstants.Tunnel.MAX_WORKERS,
    ): Int {
        val group = MrttConstants.Tunnel.WORKERS_PER_GROUP
        val normalizedMaximum = (maximum.coerceAtLeast(group) / group) * group
        return (requested.coerceIn(group, normalizedMaximum) / group) * group
    }

    fun maximumForHashes(hashCount: Int): Int {
        val requested = hashCount.coerceIn(1, MrttConstants.Tunnel.MAX_VK_HASHES) *
            MrttConstants.Tunnel.GROUPS_PER_VK_HASH * MrttConstants.Tunnel.WORKERS_PER_GROUP
        return normalize(requested)
    }

    fun maximumForSources(
        linkMode: Boolean,
        linkHashCount: Int,
        autoHashMode: Boolean,
        manualHashCount: Int,
    ): Int = when {
        linkMode && linkHashCount > 0 -> maximumForHashes(linkHashCount)
        autoHashMode -> MrttConstants.Tunnel.MAX_WORKERS
        else -> maximumForHashes(manualHashCount)
    }

    fun defaultMaximum(extraWorkers: Boolean): Int =
        if (extraWorkers) MrttConstants.Tunnel.MAX_WORKERS else MrttConstants.Tunnel.DEFAULT_MAX_WORKERS

    fun normalizeForHashes(requested: Int, hashCount: Int): Int =
        normalize(requested, maximumForHashes(hashCount))

    fun normalizeForHashValues(
        requested: Int,
        hashes: Iterable<String>,
        allowRedistribution: Boolean = false,
    ): Int {
        if (allowRedistribution) return normalize(requested)
        val hashCount = hashes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MrttConstants.Tunnel.MAX_VK_HASHES)
            .count()
        return normalizeForHashes(requested, hashCount)
    }

}
