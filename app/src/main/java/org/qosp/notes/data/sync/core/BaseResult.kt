package org.qosp.notes.data.sync.core

sealed class BaseResult(val message: String? = null) {
    override fun toString(): String = this::class.java.simpleName
}

object Success : BaseResult()
object InvalidConfig : BaseResult()

object ServerNotSupported : BaseResult()
object Unauthorized : BaseResult()

class GenericError(msg: String) : BaseResult(msg)
