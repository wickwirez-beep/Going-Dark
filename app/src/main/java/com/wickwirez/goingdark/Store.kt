package com.wickwirez.goingdark

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Vault {
    private const val ALIAS = "goingdark_vault"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun write(ctx: Context, name: String, text: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        File(ctx.filesDir, name).writeBytes(byteArrayOf(iv.size.toByte()) + iv + body)
    }

    fun read(ctx: Context, name: String): String? {
        val file = File(ctx.filesDir, name)
        if (!file.exists()) return null
        return try {
            val all = file.readBytes()
            val n = all[0].toInt()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 1, n))
            String(cipher.doFinal(all, 1 + n, all.size - 1 - n), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}

object Store {
    fun loadProfile(ctx: Context): Profile {
        val text = Vault.read(ctx, "profile.bin") ?: return Profile()
        return try {
            Profile.fromJson(text)
        } catch (e: Exception) {
            Profile()
        }
    }

    fun saveProfile(ctx: Context, p: Profile) {
        Vault.write(ctx, "profile.bin", p.toJson())
    }

    fun loadResults(ctx: Context): Map<String, BrokerResult> {
        val text = Vault.read(ctx, "results.bin") ?: return emptyMap()
        val map = LinkedHashMap<String, BrokerResult>()
        try {
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ls = o.getJSONArray("listings")
                val list = ArrayList<Listing>()
                for (j in 0 until ls.length()) {
                    val l = ls.getJSONObject(j)
                    list.add(Listing(l.getString("url"), l.getBoolean("strong"), l.getString("text")))
                }
                val id = o.getString("id")
                map[id] = BrokerResult(
                    id, Status.valueOf(o.getString("status")), o.getLong("at"), list, o.optString("note")
                )
            }
        } catch (e: Exception) {
            map.clear()
        }
        return map
    }

    fun saveResults(ctx: Context, results: Collection<BrokerResult>) {
        val arr = JSONArray()
        for (r in results) {
            val ls = JSONArray()
            for (l in r.listings) {
                ls.put(JSONObject().put("url", l.url).put("strong", l.strong).put("text", l.text))
            }
            arr.put(
                JSONObject().put("id", r.brokerId).put("status", r.status.name)
                    .put("at", r.checkedAt).put("listings", ls).put("note", r.note)
            )
        }
        Vault.write(ctx, "results.bin", arr.toString())
    }

    fun loadSent(ctx: Context): Map<String, Long> {
        val text = Vault.read(ctx, "sent.bin") ?: return emptyMap()
        val map = LinkedHashMap<String, Long>()
        try {
            val o = JSONObject(text)
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = o.getLong(k)
            }
        } catch (e: Exception) {
            map.clear()
        }
        return map
    }

    fun saveSent(ctx: Context, sent: Map<String, Long>) {
        val o = JSONObject()
        for ((k, v) in sent) o.put(k, v)
        Vault.write(ctx, "sent.bin", o.toString())
    }
}
