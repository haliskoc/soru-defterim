package com.sorudefteri.data.worker

/** Toplu AI çözüm kuyruğunun anlık ilerlemesi. */
data class BatchSolveProgress(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val running: Boolean
)
