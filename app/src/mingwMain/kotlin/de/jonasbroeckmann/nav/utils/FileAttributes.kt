package de.jonasbroeckmann.nav.utils

import kotlinx.io.files.Path
import platform.windows.*

value class FileAttributes(private val raw: UInt) : FileAttributesResult {
    val isReadOnly get() = raw mask FILE_ATTRIBUTE_READONLY
    val isHidden get() = raw mask FILE_ATTRIBUTE_HIDDEN
    val isSystem get() = raw mask FILE_ATTRIBUTE_SYSTEM
    val isDirectory get() = raw mask FILE_ATTRIBUTE_DIRECTORY
    val isArchive get() = raw mask FILE_ATTRIBUTE_ARCHIVE
    val isDevice get() = raw mask FILE_ATTRIBUTE_DEVICE
    val isNormal get() = raw mask FILE_ATTRIBUTE_NORMAL
    val isTemporary get() = raw mask FILE_ATTRIBUTE_TEMPORARY
    val isSparseFile get() = raw mask FILE_ATTRIBUTE_SPARSE_FILE
    val isReparsePoint get() = raw mask FILE_ATTRIBUTE_REPARSE_POINT
    val isCompressed get() = raw mask FILE_ATTRIBUTE_COMPRESSED
    val isOffline get() = raw mask FILE_ATTRIBUTE_OFFLINE
    val isNotContentIndexed get() = raw mask FILE_ATTRIBUTE_NOT_CONTENT_INDEXED
    val isEncrypted get() = raw mask FILE_ATTRIBUTE_ENCRYPTED
    val isIntegrityStream get() = raw mask 0x00008000
    val isVirtual get() = raw mask FILE_ATTRIBUTE_VIRTUAL
    val isNoScrubData get() = raw mask 0x00020000
    val hasExtendedAttributes get() = raw mask 0x00040000
    val isPinned get() = raw mask 0x00080000
    val isUnpinned get() = raw mask 0x00100000
    val isRecallOnOpen get() = raw mask 0x00040000
    val isRecallOnDataAccess get() = raw mask 0x00400000
}

sealed interface FileAttributesResult {
    data class Error(val code: UInt) : FileAttributesResult {
        val message by lazy { getMessageForErrorCode(code) ?: "Error $code" }
    }
}

fun Path.fileAttributes(): FileAttributesResult {
    val attributes = GetFileAttributesW(toString())
    if (attributes == INVALID_FILE_ATTRIBUTES) {
        return FileAttributesResult.Error(GetLastError())
    }
    return FileAttributes(attributes)
}
