package com.localdex

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.AtomicFile
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import android.sun.security.x509.*
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    companion object {
        @Volatile
        private var INSTANCE: AdbConnectionManager? = null

        fun getInstance(context: Context): AdbConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdbConnectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val PREFS_NAME = "adb_keys"
        private const val KEY_PRIVATE = "private_key"
        private const val KEY_CERT = "certificate"
        private const val IDENTITY_FILE = "adb_identity.properties"
    }

    private var mPrivateKey: PrivateKey? = null
    private var mCertificate: Certificate? = null
    private val identityFile = AtomicFile(File(context.noBackupFilesDir, IDENTITY_FILE))
    // Read only for migration from builds that placed this credential in Auto Backup.
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        setApi(Build.VERSION.SDK_INT)
        loadOrGenerateKeys()
    }

    private fun loadOrGenerateKeys() {
        if (loadIdentityFile()) {
            clearLegacyKeys()
            return
        }

        val privateKeyStr = prefs.getString(KEY_PRIVATE, null)
        val certStr = prefs.getString(KEY_CERT, null)

        if (privateKeyStr != null && certStr != null) {
            try {
                mPrivateKey = loadPrivateKey(privateKeyStr)
                mCertificate = loadCertificate(certStr)
                saveKeys()
                clearLegacyKeys()
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        generateAndSaveKeys()
        clearLegacyKeys()
    }

    private fun loadIdentityFile(): Boolean {
        return try {
            val properties = Properties()
            identityFile.openRead().use { properties.load(it) }
            val privateKeyStr = properties.getProperty(KEY_PRIVATE) ?: return false
            val certStr = properties.getProperty(KEY_CERT) ?: return false
            mPrivateKey = loadPrivateKey(privateKeyStr)
            mCertificate = loadCertificate(certStr)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun clearLegacyKeys() {
        if (prefs.contains(KEY_PRIVATE) || prefs.contains(KEY_CERT)) {
            if (!prefs.edit().remove(KEY_PRIVATE).remove(KEY_CERT).commit()) {
                throw IOException("Failed to remove backed-up ADB credentials")
            }
        }
    }

    private fun generateAndSaveKeys() {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            val keyPair = keyPairGenerator.generateKeyPair()
            val publicKey = keyPair.public
            mPrivateKey = keyPair.private

            val subject = "CN=localdex"
            val algorithmName = "SHA512withRSA"
            val expiryDate = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000 * 10) // 10 years

            val certificateExtensions = CertificateExtensions()
            certificateExtensions.set(
                "SubjectKeyIdentifier",
                SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
            )

            val x500Name = X500Name(subject)
            val notBefore = Date()
            val notAfter = Date(expiryDate)

            certificateExtensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
            val certificateValidity = CertificateValidity(notBefore, notAfter)

            val x509CertInfo = X509CertInfo()
            x509CertInfo.set("version", CertificateVersion(2))
            x509CertInfo.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Integer.MAX_VALUE))
            x509CertInfo.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
            x509CertInfo.set("subject", CertificateSubjectName(x500Name))
            x509CertInfo.set("key", CertificateX509Key(publicKey))
            x509CertInfo.set("validity", certificateValidity)
            x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
            x509CertInfo.set("extensions", certificateExtensions)

            val x509CertImpl = X509CertImpl(x509CertInfo)
            x509CertImpl.sign(mPrivateKey, algorithmName)
            mCertificate = x509CertImpl

            saveKeys()
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate ADB keys", e)
        }
    }

    private fun saveKeys() {
        val properties = Properties().apply {
            setProperty(KEY_PRIVATE, Base64.encodeToString(mPrivateKey!!.encoded, Base64.NO_WRAP))
            setProperty(KEY_CERT, Base64.encodeToString(mCertificate!!.encoded, Base64.NO_WRAP))
        }
        var output: FileOutputStream? = null
        try {
            output = identityFile.startWrite()
            properties.store(output, null)
            identityFile.finishWrite(output)
        } catch (e: Exception) {
            if (output != null) identityFile.failWrite(output)
            throw e
        }
    }

    private fun loadPrivateKey(encoded: String): PrivateKey {
        val keyBytes = Base64.decode(encoded, Base64.DEFAULT)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(keyBytes))
    }

    private fun loadCertificate(encoded: String): Certificate {
        val certBytes = Base64.decode(encoded, Base64.DEFAULT)
        val certFactory = java.security.cert.CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(certBytes.inputStream())
    }

    public override fun getPrivateKey(): PrivateKey = mPrivateKey!!

    public override fun getCertificate(): Certificate = mCertificate!!

    public override fun getDeviceName(): String = "localdex"
}
