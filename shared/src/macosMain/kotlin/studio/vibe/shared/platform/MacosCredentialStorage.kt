@file:OptIn(ExperimentalForeignApi::class)

package studio.vibe.shared.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import studio.vibe.shared.core.common.CredentialStorage

private const val SERVICE_NAME = "com.vibestudio"

/**
 * macOS Keychain-backed credential storage using pure CoreFoundation APIs.
 *
 * All strings are created as CFString via [CFStringCreateWithCString].
 * Data is stored/retrieved as CFData to avoid ObjC/CF bridge type issues.
 */
class MacosCredentialStorage : CredentialStorage {

    override suspend fun save(account: String, value: String): Unit = withContext(Dispatchers.Default) {
        deleteInternal(account)

        val serviceStr = CFStringCreateWithCString(kCFAllocatorDefault, SERVICE_NAME, kCFStringEncodingUTF8)
        val accountStr = CFStringCreateWithCString(kCFAllocatorDefault, account, kCFStringEncodingUTF8)
        val valueBytes = value.encodeToByteArray()
        val cfData = valueBytes.usePinned { pinned ->
            CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), valueBytes.size.toLong())
        }

        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 4, null, null)
        if (query != null && serviceStr != null && accountStr != null && cfData != null) {
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(query, kSecAttrService, serviceStr)
                CFDictionarySetValue(query, kSecAttrAccount, accountStr)
                CFDictionarySetValue(query, kSecValueData, cfData)
                SecItemAdd(query, null)
            } finally {
                CFRelease(query)
                CFRelease(serviceStr)
                CFRelease(accountStr)
                CFRelease(cfData)
            }
        }
    }

    override suspend fun load(account: String): String? = withContext(Dispatchers.Default) {
        val serviceStr = CFStringCreateWithCString(kCFAllocatorDefault, SERVICE_NAME, kCFStringEncodingUTF8)
        val accountStr = CFStringCreateWithCString(kCFAllocatorDefault, account, kCFStringEncodingUTF8)
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)

        if (query == null || serviceStr == null || accountStr == null) {
            if (query != null) CFRelease(query)
            if (serviceStr != null) CFRelease(serviceStr)
            if (accountStr != null) CFRelease(accountStr)
            return@withContext null
        }

        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceStr)
            CFDictionarySetValue(query, kSecAttrAccount, accountStr)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)

            memScoped {
                val resultRef = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, resultRef.ptr)

                if (status == errSecSuccess && resultRef.value != null) {
                    @Suppress("UNCHECKED_CAST")
                    val cfData = resultRef.value as CFDataRef
                    val length = CFDataGetLength(cfData).toInt()
                    if (length == 0) return@memScoped ""
                    val ptr = CFDataGetBytePtr(cfData)
                        ?: return@memScoped null
                    ptr.reinterpret<ByteVar>().readBytes(length).decodeToString()
                } else {
                    null
                }
            }
        } finally {
            CFRelease(query)
            CFRelease(serviceStr)
            CFRelease(accountStr)
        }
    }

    override suspend fun delete(account: String): Unit = withContext(Dispatchers.Default) {
        deleteInternal(account)
    }

    private fun deleteInternal(account: String) {
        val serviceStr = CFStringCreateWithCString(kCFAllocatorDefault, SERVICE_NAME, kCFStringEncodingUTF8)
        val accountStr = CFStringCreateWithCString(kCFAllocatorDefault, account, kCFStringEncodingUTF8)
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null)

        if (query != null && serviceStr != null && accountStr != null) {
            try {
                CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(query, kSecAttrService, serviceStr)
                CFDictionarySetValue(query, kSecAttrAccount, accountStr)
                SecItemDelete(query)
            } finally {
                CFRelease(query)
                CFRelease(serviceStr)
                CFRelease(accountStr)
            }
        }
    }
}
